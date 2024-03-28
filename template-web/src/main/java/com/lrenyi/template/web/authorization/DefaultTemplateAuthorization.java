package com.lrenyi.template.web.authorization;

import java.util.Set;

public class DefaultTemplateAuthorization implements TemplateAuthorization {
    @Override
    public boolean authorization(Set<String> scopes, String requestUri) {
        return true;
    }
}
