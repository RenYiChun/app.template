package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.core.exception.TemplateException;
import lombok.Getter;

/**
 * 携带 HTTP 状态码与业务错误码的 Dataforge 异常。
 * 供 {@link DataforgeExceptionHandler} 返回对应 HTTP 状态，并将 {@link #getErrorCode()} 写入 Result.code。
 */
@Getter
public class DataforgeHttpException extends TemplateException {

    private final int httpStatus;

    public DataforgeHttpException(int httpStatus, int businessCode, String message) {
        super(businessCode, message);
        this.httpStatus = httpStatus;
    }

    public DataforgeHttpException(int httpStatus, int businessCode, String message, Throwable cause) {
        super(businessCode, message, cause);
        this.httpStatus = httpStatus;
    }
}
