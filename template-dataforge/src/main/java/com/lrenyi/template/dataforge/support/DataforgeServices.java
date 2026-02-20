package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;

public record DataforgeServices(EntityRegistry entityRegistry, ActionRegistry actionRegistry,
                                EntityCrudService crudService, DataforgeProperties properties,
                                DataforgePermissionChecker permissionChecker, ObjectMapper objectMapper,
                                ObjectProvider<Validator> validatorProvider, ConversionService conversionService) {}
