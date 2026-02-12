-- Generated from @Domain. Apply via Flyway or manually.
<#list entities as entity>
-- ${entity.simpleName} -> ${entity.tableName}
CREATE TABLE IF NOT EXISTS ${entity.tableName} (
<#list entity.fields as f>
    ${f.columnName} <#if f.type == "Long" || f.type == "Integer">BIGINT<#elseif f.type == "Boolean">TINYINT(1)<#else>VARCHAR(${f.length!255})</#if><#if f.id && f.generatedValue> AUTO_INCREMENT</#if><#if !f.nullable!true> NOT NULL</#if><#if f.id> PRIMARY KEY</#if><#if f_has_next>,</#if>
</#list>
);
</#list>
