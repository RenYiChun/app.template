package com.lrenyi.template.flow.executor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 信号量受控的虚拟线程执行器。
 * 提交任务时先获取信号量许可，再在虚拟线程中执行，执行结束或异常时释放许可。
 * 并发度由 Semaphore 许可数决定。
 * <p>
 * 支持可插拔的 {@link PermitStrategy}，用于自定义 acquire/release 逻辑（如公平 acquire）。
 */
@Slf4j
public class BoundedVirtualExecutor implements ExecutorService {
    
    private final ExecutorService delegate;
    private final PermitStrategy defaultStrategy;
    /** 为 true 时 execute() 在调用线程先 acquire 再提交，用于 Caffeine removal 等需在提交处背压的场景 */
    private final boolean blockCallerOnExecute;
    private volatile boolean shutdown;
    
    public BoundedVirtualExecutor(Semaphore semaphore) {
        this(semaphore, Executors.newVirtualThreadPerTaskExecutor(), false);
    }
    
    /**
     * @param blockCallerOnExecute true 时 execute() 在调用线程先 acquire 再提交，调用方会在无许可时阻塞（用于驱逐回调等背压）
     */
    public BoundedVirtualExecutor(Semaphore semaphore, ExecutorService delegate, boolean blockCallerOnExecute) {
        if (semaphore == null) {
            throw new IllegalArgumentException("semaphore 非 null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 非 null");
        }
        PermitStrategy strategy = defaultStrategy(semaphore);
        this.delegate = delegate;
        this.defaultStrategy = strategy;
        this.blockCallerOnExecute = blockCallerOnExecute;
    }
    
    /** 默认策略：简单 semaphore.acquire/release */
    private static PermitStrategy defaultStrategy(Semaphore semaphore) {
        return new PermitStrategy() {
            @Override
            public void acquire() throws InterruptedException {
                semaphore.acquire();
            }
            
            @Override
            public void release() {
                semaphore.release();
            }
        };
    }
    
    /** 使用自定义 PermitStrategy（如 PermitPair 双层限流） */
    public BoundedVirtualExecutor(PermitStrategy strategy) {
        this(strategy, Executors.newVirtualThreadPerTaskExecutor(), false);
    }
    
    private BoundedVirtualExecutor(PermitStrategy strategy, ExecutorService delegate, boolean blockCallerOnExecute) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy 非 null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 非 null");
        }
        this.delegate = delegate;
        this.defaultStrategy = strategy;
        this.blockCallerOnExecute = blockCallerOnExecute;
    }
    
    @Override
    public void execute(@NonNull Runnable command) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        if (blockCallerOnExecute) {
            try {
                defaultStrategy.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExecutorInterruptedException(e);
            } catch (TimeoutException e) {
                throw new ExecutorAcquireTimeoutException(e);
            }
            delegate.execute(runReleaseOnly(defaultStrategy, command));
        } else {
            delegate.execute(runWithStrategy(defaultStrategy, command));
        }
    }
    
    /**
     * 只释放信号量，不获取（用于已在外部 acquire 的场景）。
     * 消费许可必须在 finally 中释放，否则会导致许可泄漏、耗尽（使用中=上限且等待升高）；若 release() 抛出也会泄漏。
     */
    private static Runnable runReleaseOnly(PermitStrategy strategy, Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                try {
                    strategy.release();
                } catch (Throwable t) {
                    // release() 异常会导致许可未释放、耗尽，打日志便于排查「消费过程中未正常释放」类问题
                    log.warn("Consumer permit release failed, permit may be leaked (exhausted permits): {}", t.getMessage());
                    throw t;
                }
            }
        };
    }
    
    /**
     * 获取并释放信号量（用于 execute 等标准方法）
     */
    private static Runnable runWithStrategy(PermitStrategy strategy, Runnable task) {
        return () -> {
            try {
                strategy.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExecutorInterruptedException(e);
            } catch (TimeoutException e) {
                throw new ExecutorAcquireTimeoutException(e);
            }
            try {
                task.run();
            } finally {
                strategy.release();
            }
        };
    }
    
    @Override
    public void shutdown() {
        shutdown = true;
        delegate.shutdown();
    }
    
    @Override
    public @NonNull List<Runnable> shutdownNow() {
        shutdown = true;
        return delegate.shutdownNow();
    }
    
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }
    
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }
    
    @Override
    public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
    
    @Override
    public <T> @NonNull Future<T> submit(@NonNull Callable<T> task) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.submit(wrap(task));
    }
    
    @Override
    public <T> @NonNull Future<T> submit(@NonNull Runnable task, T result) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.submit(wrap(task), result);
    }
    
    @Override
    public @NonNull Future<?> submit(@NonNull Runnable task) {
        return submit(task, null);
    }
    
    @Override
    public <T> @NonNull List<Future<T>> invokeAll(
            @NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
    }
    
    @Override
    public <T> @NonNull List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks,
            long timeout,
            @NonNull TimeUnit unit) throws InterruptedException {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
    }
    
    @Override
    public <T> @NonNull T invokeAny(
            @NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
    }
    
    @Override
    public <T> @NonNull T invokeAny(@NonNull Collection<? extends Callable<T>> tasks,
            long timeout,
            @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
    }
    
    private Runnable wrap(Runnable task) {
        return runWithStrategy(defaultStrategy, task);
    }
    
    private <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            try {
                defaultStrategy.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExecutorInterruptedException(e);
            } catch (TimeoutException e) {
                throw new ExecutorAcquireTimeoutException(e);
            }
            try {
                return task.call();
            } finally {
                defaultStrategy.release();
            }
        };
    }
    
    /**
     * 使用自定义策略提交任务（如公平 acquire 的消费任务）。
     * 先在调用线程获取信号量，避免大量虚拟线程阻塞占用内存。
     */
    public void submitWithStrategy(@NonNull PermitStrategy strategy, @NonNull Runnable task) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        // 在调用线程获取信号量
        try {
            strategy.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutorInterruptedException(e);
        } catch (TimeoutException e) {
            throw new ExecutorAcquireTimeoutException(e);
        }
        // 提交任务时只负责释放（已在外部 acquire）
        delegate.execute(runReleaseOnly(strategy, task));
    }
    
    /**
     * 许可获取与释放策略，用于自定义 acquire/release 逻辑。
     */
    public interface PermitStrategy {
        void acquire() throws InterruptedException, TimeoutException;
        
        void release();
    }
}
