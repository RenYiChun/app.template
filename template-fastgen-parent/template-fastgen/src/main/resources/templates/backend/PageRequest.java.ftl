package ${basePackage}.page;

import lombok.Getter;
import lombok.Setter;

/**
 * Generated from @Page ${page.simpleName}.
 * Request DTO for ${page.title} 表单提交.
 */
@Getter
@Setter
public class ${page.simpleName}Request {

<#list page.fields as field>
    private ${field.type} ${field.name};

</#list>
}
