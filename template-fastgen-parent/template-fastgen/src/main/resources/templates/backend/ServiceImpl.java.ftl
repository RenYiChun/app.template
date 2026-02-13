package ${basePackage}.service;

import ${basePackage}.domain.${entity.simpleName};
import ${basePackage}.mapper.${entity.simpleName}Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Generated default implementation for ${entity.simpleName}.
 * Replace with your own @Service bean to override (Strategy C).
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ${entity.simpleName}ServiceImpl implements ${entity.simpleName}Service {

    private final ${entity.simpleName}Mapper ${entity.simpleName?uncap_first}Mapper;

    public ${entity.simpleName}ServiceImpl(${entity.simpleName}Mapper ${entity.simpleName?uncap_first}Mapper) {
        this.${entity.simpleName?uncap_first}Mapper = ${entity.simpleName?uncap_first}Mapper;
    }

    @Override
    public List<${entity.simpleName}> listByPage(int page, int size) {
        int offset = (page - 1) * size;
        return ${entity.simpleName?uncap_first}Mapper.selectByPage(offset, size);
    }

    @Override
    public ${entity.simpleName} getById(Long id) {
        return ${entity.simpleName?uncap_first}Mapper.selectById(id);
    }

    @Override
    public ${entity.simpleName} save(${entity.simpleName} entity) {
        ${entity.simpleName?uncap_first}Mapper.insert(entity);
        return entity;
    }

    @Override
    public ${entity.simpleName} update(${entity.simpleName} entity) {
        ${entity.simpleName?uncap_first}Mapper.update(entity);
        return entity;
    }

    @Override
    public void deleteById(Long id) {
        ${entity.simpleName?uncap_first}Mapper.deleteById(id);
    }
}
