-- Generated from @Domain annotations. Apply via Flyway or manually.
<#list entities as entity>

-- Table: ${entity.tableName} (from ${entity.simpleName})
CREATE TABLE IF NOT EXISTS ${entity.tableName} (
<#list entity.fields as f>
    ${f.columnName} <#if f.type == "Long" || f.type == "Integer">BIGINT<#elseif f.type == "Boolean">TINYINT(1)<#elseif f.type == "java.time.LocalDate">DATE<#elseif f.type == "java.time.LocalDateTime">DATETIME<#elseif f.type == "java.math.BigDecimal">DECIMAL(19,2)<#else>VARCHAR(${f.length!255})</#if><#if f.id && f.generatedValue> AUTO_INCREMENT</#if><#if f.nullable?? && !f.nullable> NOT NULL</#if><#if f.id> PRIMARY KEY</#if><#if f_has_next>,</#if>
</#list>
);
</#list>
