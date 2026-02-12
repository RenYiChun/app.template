/**
 * Generated from @Domain ${entity.simpleName}.
 */
export interface ${entity.simpleName} {
<#list entity.fields as f>
  ${f.name}: ${f.type};
</#list>
}
