package com.lrenyi.template.dataforge.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 通用 Mapper 接口，所有生成的 Mapper 都继承此接口。
 * 
 * @param <E> Entity 类型
 * @param <C> CreateDTO 类型
 * @param <U> UpdateDTO 类型
 * @param <R> ResponseDTO 类型
 * @param <P> PageResponseDTO 类型
 */
public interface BaseMapper<E, C, U, R, P> {

    E toEntity(C createDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(U updateDto, @MappingTarget E entity);

    R toResponse(E entity);

    P toPageResponse(E entity);
}
