package org.springframework.security.oauth2.core.http.converter;

public class TemplateInitOauth {
    public static void init() {
        HttpMessageConverters.loaderAtSpringboot();
    }
}
