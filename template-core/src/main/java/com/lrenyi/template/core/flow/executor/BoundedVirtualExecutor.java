package com.lrenyi.template.core.flow.executor;

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

/**
 * 信号量受控的虚拟线程执行器。
 * 提交任务时先获取信号量许可，再在虚拟线程中执行，执行结束或异常时释放许可。
 * 并发度由 Semaphore 许可数决定。
 * <p>
 * 支持可插拔的 {@link PermitStrategy}，用于自定义 acquire/release 逻辑（如公平 acquire）。
 */
public class BoundedVirtualExecutor implements ExecutorService {
    
    /**
     * 许可获取与释放策略，用于自定义 acquire/release 逻辑。
     */
    public interface PermitStrategy {
        void acquire() throws InterruptedException;
        
        void release();
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
    
    private final ExecutorService delegate;
    private final PermitStrategy defaultStrategy;
    private volatile boolean shutdown;
    
    public BoundedVirtualExecutor(Semaphore semaphore) {
        this(semaphore, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    public BoundedVirtualExecutor(Semaphore semaphore, ExecutorService delegate) {
        if (semaphore == null) {
            throw new IllegalArgumentException("semaphore 非 null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 非 null");
        }
        this.delegate = delegate;
        this.defaultStrategy = defaultStrategy(semaphore);
    }
    
    @Override
    public void execute(@NonNull Runnable command) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        delegate.execute(runWithStrategy(defaultStrategy, command));
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
    
    /**
     * 使用自定义策略提交任务（如公平 acquire 的消费任务）。
     */
    public void submitWithStrategy(@NonNull PermitStrategy strategy, @NonNull Runnable task) {
        if (shutdown) {
            throw new IllegalStateException("Executor 已关闭");
        }
        delegate.execute(runWithStrategy(strategy, task));
    }
    
    private static Runnable runWithStrategy(PermitStrategy strategy, Runnable task) {
        return () -> {
            try {
                strategy.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            try {
                task.run();
            } finally {
                strategy.release();
            }
        };
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
    
    private <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            try {
                defaultStrategy.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            try {
                return task.call();
            } finally {
                defaultStrategy.release();
            }
        };
    }
    
    private Runnable wrap(Runnable task) {
        return runWithStrategy(defaultStrategy, task);
    }
}
