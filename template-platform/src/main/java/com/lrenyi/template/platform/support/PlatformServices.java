package com.lrenyi.template.platform.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.config.PlatformProperties;
import com.lrenyi.template.platform.permission.PlatformPermissionChecker;
import com.lrenyi.template.platform.registry.ActionRegistry;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.service.EntityCrudService;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;

@Getter
@RequiredArgsConstructor
public class PlatformServices {
    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final EntityCrudService crudService;
    private final PlatformProperties properties;
    private final PlatformPermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Validator> validatorProvider;
    private final ConversionService conversionService;
}
