package com.lrenyi.oauth2.service.oauth2.password;

import java.util.List;
import com.lrenyi.oauth2.service.config.IdentifierType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class RbacUserDetailsService implements UserDetailsService {

    private final IRbacService rbacService;

    public RbacUserDetailsService(ObjectProvider<IRbacService> rbacServiceObjectProvider) {
        this.rbacService = rbacServiceObjectProvider.getIfAvailable();
    }

    @Override
    @Cacheable(value = "userDetails", key = "#username")
    @SuppressWarnings({"null", "unused"})
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (rbacService == null) {
            throw new UsernameNotFoundException("rbacService is null, please create it first");
        }
        String[] parts = username.split(":", 2);
        if (parts.length != 2) {
            throw new UsernameNotFoundException("Username must be in the format 'type:identifier'");
        }
        IdentifierType identifierType;
        try {
            identifierType = IdentifierType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid identifier type: " + parts[0]);
        }
        String identifier = parts[1];
        RbacUserCredentials credentials = rbacService.loadUserCredentials(identifier, identifierType);
        if (credentials == null) {
            throw new UsernameNotFoundException("User not found for identifier: " + identifier);
        }
        List<SimpleGrantedAuthority> authorities = credentials.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new org.springframework.security.core.userdetails.User(
                credentials.username(),
                credentials.password(),
                authorities);
    }
}
