package com.lrenyi.template.service.config;

import com.lrenyi.template.service.security.CustomFeignClientInterceptor;
import com.lrenyi.template.web.utils.ConverterCustomizer;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {
    
    /**
     * 要实现这个功能，必须将HttpMessageConverters类中converters属性修改为是可以更改的map
     * 在原先的狗仔函数中这个参数被赋予了一个不能更新的map，所以才更新了社区版本的源码
     */
    @Bean
    public HttpMessageConverterCustomizer customizer() {
        return ConverterCustomizer::replace;
    }
    
    @Bean
    public RequestInterceptor feignClientInterceptor() {
        return new CustomFeignClientInterceptor();
    }
}
