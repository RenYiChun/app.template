package com.lrenyi.template.web.function.log;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OperateLogVo {
    /**
     * 操作者
     */
    private String operator;
    /**
     * 在什么时间进行的操作
     */
    private LocalDateTime time;
    /**
     * 在什么地方进行的操作
     */
    private String location;
    /**
     * 对什么对象进行的操作
     */
    private String object;
    /**
     * 进行的什么操作
     */
    private String operation;
    /**
     * 操作的结果状态：成功true或失败false
     */
    private boolean resultStatus;
    /**
     * 操作携带的输入参数，json字符串
     */
    private String requestParameters;
    /**
     * 操作成功之后的返回结果：json字符串
     */
    private String returnResults;
    /**
     * 错误消息
     */
    private String errorMessage;
}
