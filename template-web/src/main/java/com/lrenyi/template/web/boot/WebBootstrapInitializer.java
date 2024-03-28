package com.lrenyi.template.web.boot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;

@Slf4j
public class WebBootstrapInitializer implements BootstrapRegistryInitializer {
    @Override
    public void initialize(BootstrapRegistry registry) {
        //一下对应用中使用的JSON做一些全局配置
        JSON.config(JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
