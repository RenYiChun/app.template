package com.lrenyi.template.core;

import com.lrenyi.template.core.config.ConfigImportSelect;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ComponentScan
@Import(ConfigImportSelect.class)
@Configuration(proxyBeanMethods = false)
public class TemplateCoreAutoConfig {

}
