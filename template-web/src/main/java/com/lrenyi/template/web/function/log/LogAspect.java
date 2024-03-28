package com.lrenyi.template.web.function.log;

import com.alibaba.fastjson2.JSON;
import com.lrenyi.template.core.util.SpringContextUtil;
import com.lrenyi.template.core.util.StringUtils;
import com.lrenyi.template.core.util.SystemUtils;
import com.lrenyi.template.web.config.properties.LogConfig;
import com.lrenyi.template.web.function.Log;
import com.lrenyi.template.web.function.log.service.IOperateLogAspectService;
import com.lrenyi.template.web.utils.ServletUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
public class LogAspect {
    
    @Resource
    private LogConfig logConfig;
    
    @Pointcut("@annotation(com.lrenyi.template.web.function.Log)")
    private void pointcut() {
        
    }
    
    @AfterReturning(pointcut = "pointcut()", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Object jsonResult) {
        handleLog(joinPoint, null, jsonResult);
    }
    
    protected void handleLog(final JoinPoint joinPoint,
            final Exception exception,
            Object jsonResult) {
        Log logAnnotation = getAnnotationLog(joinPoint);
        if (logAnnotation == null) {
            return;
        }
        IOperateLogAspectService service = SpringContextUtil.getBean(IOperateLogAspectService.class);
        if (service == null) {
            log.warn("unable to obtain service for the operation log, saving the operation log "
                             + "failed.");
            return;
        }
        OperateLogVo operateLog = new OperateLogVo();
        HttpServletRequest request = ServletUtils.getRequest();
        String ip = SystemUtils.getIpAddr(request);
        
        String authorization = request.getHeader("username");
        if (StringUtils.hasLength(authorization)) {
            operateLog.setOperator(authorization);
        }
        operateLog.setLocation(ip);
        operateLog.setTime(LocalDateTime.now());
        operateLog.setObject(logAnnotation.object());
        operateLog.setOperation(logAnnotation.operation());
        operateLog.setResultStatus(exception == null);
        operateLog.setErrorMessage(exception != null ? exception.getMessage() : "");
        boolean dataSave = logAnnotation.dataSave();
        if (dataSave && logConfig.isDataSave()) {
            operateLog.setReturnResults(JSON.toJSONString(jsonResult));
            String method = request.getMethod();
            saveRequestValue(method, joinPoint, operateLog);
        }
        service.logHandle(operateLog);
    }
    
    private Log getAnnotationLog(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        
        if (method != null) {
            return method.getAnnotation(Log.class);
        }
        return null;
    }
    
    private void saveRequestValue(String method, JoinPoint joinPoint, OperateLogVo openerLog) {
        if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
            String params = argsArrayToString(joinPoint.getArgs());
            openerLog.setRequestParameters(params);
        }
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String[]> parameterMap = ServletUtils.getRequest().getParameterMap();
            String s = JSON.toJSONString(parameterMap);
            openerLog.setRequestParameters(s);
        }
    }
    
    private String argsArrayToString(Object[] paramsArray) {
        List<Object> value = new ArrayList<>();
        if (paramsArray != null) {
            for (Object o : paramsArray) {
                if (isFilterObject(o)) {
                    continue;
                }
                value.add(o);
            }
        }
        return JSON.toJSONString(value);
    }
    
    private boolean isFilterObject(final Object o) {
        return o.getClass().getName().contains("MultipartFile") || o instanceof HttpServletRequest
                || o instanceof HttpServletResponse;
    }
    
    @AfterThrowing(value = "pointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e, null);
    }
}
