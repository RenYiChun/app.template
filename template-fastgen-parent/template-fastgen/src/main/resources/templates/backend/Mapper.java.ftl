package ${basePackage}.${entity.simpleName?lower_case};

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Generated from @Domain ${entity.simpleName}.
 * MyBatis Mapper for ${entity.displayName} CRUD.
 */
@Mapper
public interface ${entity.simpleName}Mapper {

    /**
     * 分页查询。
     */
    @Select("SELECT * FROM ${entity.tableName} LIMIT ${r"#{offset}"}, ${r"#{size}"}")
    List<${entity.simpleName}> selectByPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 根据 ID 查询。
     */
    @Select("SELECT * FROM ${entity.tableName} WHERE <#list entity.fields as field><#if field.id>${field.columnName}</#if></#list> = ${r"#{id}"}")
    ${entity.simpleName} selectById(@Param("id") Long id);

    /**
     * 插入记录。
     */
    @Insert("INSERT INTO ${entity.tableName} (<#list entity.fields as field><#if !field.generatedValue>${field.columnName}<#if field_has_next>, </#if></#if></#list>) " +
        "VALUES (<#list entity.fields as field><#if !field.generatedValue>${r"#{"}${field.name}${r"}"}<#if field_has_next>, </#if></#if></#list>)")
    @Options(useGeneratedKeys = true, keyProperty = "<#list entity.fields as field><#if field.id>${field.name}</#if></#list>")
    void insert(${entity.simpleName} entity);

    /**
     * 更新记录。
     */
    @Update("UPDATE ${entity.tableName} SET <#list entity.fields as field><#if !field.id>${field.columnName} = ${r"#{"}${field.name}${r"}"}<#if field_has_next>, </#if></#if></#list> " +
        "WHERE <#list entity.fields as field><#if field.id>${field.columnName}</#if></#list> = <#list entity.fields as field><#if field.id>${r"#{"}${field.name}${r"}"}</#if></#list>")
    void update(${entity.simpleName} entity);

    /**
     * 根据 ID 删除。
     */
    @Delete("DELETE FROM ${entity.tableName} WHERE <#list entity.fields as field><#if field.id>${field.columnName}</#if></#list> = ${r"#{id}"}")
    void deleteById(@Param("id") Long id);
}
