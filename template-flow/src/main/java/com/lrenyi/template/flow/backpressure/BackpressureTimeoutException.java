package com.lrenyi.template.flow.backpressure;

import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.resource.PermitPair;
import lombok.Getter;

/**
 * 背压超时异常，携带 {@link PermitPair.AcquireResult} 以便 Manager 按 global/per-job 打点。
 */
@Getter
public final class BackpressureTimeoutException extends TimeoutException {

    private final PermitPair.AcquireResult result;

    public BackpressureTimeoutException(String message, PermitPair.AcquireResult result) {
        super(message);
        this.result = result;
    }

}
