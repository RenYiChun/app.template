package com.lrenyi.template.service;

import com.lrenyi.template.service.config.ConfigImportSelect;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import(ConfigImportSelect.class)
@Configuration(proxyBeanMethods = false)
public class TemplateServiceAutoConfig {

}
