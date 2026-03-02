package com.lrenyi.template.dataforge.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.lrenyi.template.dataforge.mapper.BaseMapper;
import org.springframework.core.GenericTypeResolver;

/**
 * 管理所有的 BaseMapper 实现，提供通过 Entity 类型查找对应 Mapper 的能力。
 */
public class EntityMapperProvider {
    
    private final Map<Class<?>, MapperInfo> mapperCache = new ConcurrentHashMap<>();
    
    public EntityMapperProvider(List<BaseMapper<?, ?, ?, ?, ?>> mappers) {
        if (mappers != null) {
            for (BaseMapper<?, ?, ?, ?, ?> mapper : mappers) {
                registerMapper(mapper);
            }
        }
    }
    
    private void registerMapper(BaseMapper<?, ?, ?, ?, ?> mapper) {
        // MapStruct 生成的实现类通常会保留泛型信息，或者是具体的类型
        // 我们需要解析 BaseMapper<E, C, U, R, P> 中的泛型参数
        Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(mapper.getClass(), BaseMapper.class);
        if (generics != null && generics.length >= 1) {
            Class<?> entityClass = generics[0];
            Class<?> createDtoClass = generics.length > 1 ? generics[1] : null;
            Class<?> updateDtoClass = generics.length > 2 ? generics[2] : null;
            Class<?> responseDtoClass = generics.length > 3 ? generics[3] : null;
            Class<?> pageResponseDtoClass = generics.length > 4 ? generics[4] : null;
            
            mapperCache.put(entityClass,
                            new MapperInfo(mapper,
                                           createDtoClass,
                                           updateDtoClass,
                                           responseDtoClass,
                                           pageResponseDtoClass
                            )
            );
        }
    }
    
    public MapperInfo getMapperInfo(Class<?> entityClass) {
        return mapperCache.get(entityClass);
    }
    
    public record MapperInfo(BaseMapper<?, ?, ?, ?, ?> mapper, Class<?> createDtoClass, Class<?> updateDtoClass,
                             Class<?> responseDtoClass, Class<?> pageResponseDtoClass) {}
}
