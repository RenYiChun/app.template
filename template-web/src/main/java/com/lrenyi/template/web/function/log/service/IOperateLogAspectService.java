package com.lrenyi.template.web.function.log.service;

import com.lrenyi.template.web.function.log.OperateLogVo;

public interface IOperateLogAspectService {
    void logHandle(OperateLogVo log);
}
