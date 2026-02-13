package ${basePackage}.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Generated from @Domain ${entity.simpleName}.
 * DO NOT edit in generated layer; extend or replace in your module.
 */
@Entity
@Getter
@Setter
@Table(name = "${entity.tableName}")
public class ${entity.simpleName} {

<#list entity.fields as f>
<#if f.id>
    @Id
<#if f.generatedValue>
    @GeneratedValue(strategy = GenerationType.IDENTITY)
</#if>
</#if>
<#if f.columnName??>
    @Column(name = "${f.columnName}"<#if f.length??>, length = ${f.length}</#if><#if f.nullable?? && !f.nullable>, nullable = false</#if>)
</#if>
    private ${f.type} ${f.name};

</#list>
}
