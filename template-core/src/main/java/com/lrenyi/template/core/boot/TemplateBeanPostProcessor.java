package com.lrenyi.template.core.boot;

import static com.lrenyi.template.core.config.ThreadConstant.SCHEDULED_EXECUTOR_SERVICE;
import static com.lrenyi.template.core.config.ThreadConstant.VIRTUAL_THREAD_EXECUTOR;

import com.lrenyi.template.core.annotation.TemplateThread;
import com.lrenyi.template.core.config.ThreadConstant;
import java.lang.reflect.Method;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TemplateBeanPostProcessor implements BeanPostProcessor {
    
    private static Thread makeTemplateThread(Object bean, Method method, TemplateThread thread) {
        Thread templateThread = new Thread(() -> {
            try {
                method.invoke(bean);
            } catch (Throwable cause) {
                log.error("run method {} error in a thread", method.getName(), cause);
            }
        });
        String name = thread.name();
        if (StringUtils.hasLength(name)) {
            templateThread.setName(ThreadConstant.NAME_PREFIX + name);
        } else {
            String methodName = method.getName();
            templateThread.setName(ThreadConstant.NAME_PREFIX + methodName);
        }
        if (thread.priority() != -1) {
            templateThread.setPriority(thread.priority());
        }
        templateThread.setDaemon(thread.daemon());
        templateThread.setUncaughtExceptionHandler((t, e) -> {
            log.error("{}线程发生未捕获异常", t.getName(), e);
            Thread newThread = makeTemplateThread(bean, method, thread);
            newThread.start();
        });
        return templateThread;
    }
    
    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean,
                                                  @NonNull String beanName) throws BeansException {
        final Class<?> clazz = bean.getClass();
        for (Method method : clazz.getMethods()) {
            TemplateThread thread = AnnotationUtils.findAnnotation(method, TemplateThread.class);
            if (thread == null) {
                continue;
            }
            if (thread.virtually()) {
                long timeOut = thread.timeOut();
                Runnable command = () -> {
                    try {
                        method.invoke(bean);
                    } catch (Throwable cause) {
                        log.error("run method {} error in a thread", method.getName(), cause);
                    }
                };
                if (timeOut == -1) {
                    VIRTUAL_THREAD_EXECUTOR.execute(command);
                } else {
                    SCHEDULED_EXECUTOR_SERVICE.scheduleWithFixedDelay(command, timeOut / 2, timeOut, thread.unit());
                }
            } else {
                Thread templateThread = makeTemplateThread(bean, method, thread);
                templateThread.start();
            }
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }
}
