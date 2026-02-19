package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;

@Getter
@RequiredArgsConstructor
public class DataforgeServices {
    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final EntityCrudService crudService;
    private final DataforgeProperties properties;
    private final DataforgePermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Validator> validatorProvider;
    private final ConversionService conversionService;
}
