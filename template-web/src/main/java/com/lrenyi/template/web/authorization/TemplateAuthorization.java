package com.lrenyi.template.web.authorization;

import java.util.Set;

public interface TemplateAuthorization {
    boolean authorization(Set<String> scopes, String requestUri);
}
