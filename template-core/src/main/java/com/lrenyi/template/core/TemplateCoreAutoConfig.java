package com.lrenyi.template.core;

import com.lrenyi.template.core.config.CoreConfigImportSelect;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ComponentScan
@Import(CoreConfigImportSelect.class)
@Configuration(proxyBeanMethods = false)
public class TemplateCoreAutoConfig {

}
