/**
 * Generated from @Domain ${entity.simpleName}.
 * TypeScript type definition for ${entity.displayName}.
 */
export interface ${entity.simpleName} {
<#list entity.fields as f>
  ${f.name}<#if !f.required>?</#if>: <#if f.type == "Long" || f.type == "Integer">number<#elseif f.type == "Boolean">boolean<#elseif f.type == "java.time.LocalDate" || f.type == "java.time.LocalDateTime">string<#elseif f.type == "java.math.BigDecimal">number<#else>string</#if>;
</#list>
}
