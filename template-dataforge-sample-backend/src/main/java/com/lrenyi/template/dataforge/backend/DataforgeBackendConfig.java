package com.lrenyi.template.dataforge.backend;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Consumer;

/**
 * 平台后端扩展配置。安全由 template-api 的 DefaultSecurityFilterChainBuilder 统一处理，
 * permit-urls 通过 application.yml 的 app.template.security.permit-urls 配置。
 * PasswordEncoder 使用 DelegatingPasswordEncoder，支持 {noop}、{bcrypt} 等格式（用于 OAuth2
 * client_secret 及用户密码）。
 * 此处提供 CORS 及 Session 策略。
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataforgeBackendConfig {

    @Bean("passwordEncoder")
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 通过 template-api 的 httpConfigurerProvider 注入 CORS 与 Session 策略 */
    @Bean
    public Consumer<HttpSecurity> dataforgeHttpConfigurer(
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        return http -> {
            try {
                http.cors(c -> c.configurationSource(corsConfigurationSource));
                http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
