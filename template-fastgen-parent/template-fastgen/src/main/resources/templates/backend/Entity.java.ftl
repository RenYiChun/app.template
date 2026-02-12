package ${basePackage}.${entity.simpleName?lower_case};

/**
 * Generated from @Domain ${entity.simpleName}.
 * DO NOT edit in generated layer; extend or replace in your module.
 */
public class ${entity.simpleName} {

<#list entity.fields as f>
    private ${f.type} ${f.name};

</#list>
<#list entity.fields as f>
    public ${f.type} get${f.name?cap_first}() {
        return ${f.name};
    }

    public void set${f.name?cap_first}(${f.type} ${f.name}) {
        this.${f.name} = ${f.name};
    }

</#list>
}
