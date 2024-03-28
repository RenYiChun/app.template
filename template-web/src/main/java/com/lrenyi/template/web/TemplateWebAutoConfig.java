package com.lrenyi.template.web;

import com.alibaba.fastjson2.JSON;
import com.lrenyi.template.core.annotation.Function;
import com.lrenyi.template.core.boot.Interface;
import com.lrenyi.template.core.util.SpringContextUtil;
import com.lrenyi.template.web.authorization.RsaPublicAndPrivateKey;
import com.lrenyi.template.web.authorization.TemplateRsaPublicAndPrivateKey;
import com.lrenyi.template.web.config.ConfigImportSelect;
import com.lrenyi.template.web.converter.FastJsonHttpMessageConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

@Slf4j
@ComponentScan
@Import(ConfigImportSelect.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.template.config.enable", matchIfMissing = true)
public class TemplateWebAutoConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
        return new TemplateRsaPublicAndPrivateKey();
    }
    
    @Bean
    public FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        return new FastJsonHttpMessageConverter();
    }
    
    @EventListener(ApplicationReadyEvent.class)
    private void findAllRestFullInterface() {
        RequestMappingHandlerMapping handlerMapping = SpringContextUtil.getBean("requestMappingHandlerMapping",
                                                                                RequestMappingHandlerMapping.class
        );
        TemplateInterface templateInterface = null;
        try {
            templateInterface = SpringContextUtil.getBean(TemplateInterface.class);
        } catch (NoSuchBeanDefinitionException ignore) {}
        if (templateInterface == null) {
            return;
        }
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();
        Map<String, List<Interface>> allDomainFunction = new HashMap<>();
        methods.forEach((info, method) -> {
            Set<RequestMethod> methodSet = info.getMethodsCondition().getMethods();
            PathPatternsRequestCondition pathPatternsCondition = info.getPathPatternsCondition();
            if (pathPatternsCondition == null) {
                return;
            }
            Set<PathPattern> patterns = pathPatternsCondition.getPatterns();
            Function function = method.getMethodAnnotation(Function.class);
            List<String> collect = patterns.stream().map(PathPattern::getPatternString).toList();
            if (function == null) {
                log.warn("遇到一个没有控制的前端接口:{}", String.join(",", collect));
                return;
            }
            String domain = function.domain();
            List<Interface> interfaces = allDomainFunction.computeIfAbsent(domain, key -> new ArrayList<>());
            Interface face = new Interface();
            interfaces.add(face);
            
            face.setDomain(domain);
            face.setService(function.service());
            face.setName(function.interfaceName());
            Optional<RequestMethod> first = methodSet.stream().findFirst();
            if (first.isEmpty()) {
                log.warn("没有获取到接口{}的http方法", String.join(",", collect));
                return;
            }
            String faceMethod = first.get().name();
            if (!patterns.isEmpty()) {
                Optional<PathPattern> pattern = patterns.stream().findFirst();
                pattern.ifPresent(pathPattern -> {
                    String path = pathPattern.getPatternString();
                    path = "{" + faceMethod + "}" + path;
                    face.setPath(path);
                });
            }
        });
        allDomainFunction.forEach((domain, interfaces) -> {
            log.debug("发现一个功能域：{}， 域下共有接口{}个: ", domain, interfaces.size());
            for (Interface anInterface : interfaces) {
                log.debug(">>:{}", JSON.toJSONString(anInterface));
            }
        });
        Collection<List<Interface>> interfaces = allDomainFunction.values();
        List<Interface> faceList = new ArrayList<>();
        for (List<Interface> list : interfaces) {
            faceList.addAll(list);
        }
        templateInterface.saveInterfaceInfo(faceList);
    }
}
