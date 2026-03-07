package com.lrenyi.template.flow.executor;

public class ExecutorInterruptedException extends RuntimeException {
    public ExecutorInterruptedException(InterruptedException cause) {
        super(cause);
    }
}
