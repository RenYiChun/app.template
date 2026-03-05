package com.lrenyi.template.flow.executor;

import java.util.concurrent.TimeoutException;

public class ExecutorAcquireTimeoutException extends RuntimeException {
    public ExecutorAcquireTimeoutException(TimeoutException cause) {
        super(cause);
    }
}
