package com.lrenyi.oauth2.service.oauth2.password;

import java.util.List;
import com.lrenyi.oauth2.service.config.IdentifierType;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class RbacUserDetailsService implements UserDetailsService {
    private final ObjectProvider<IRbacService> rbacService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IRbacService iRbacService = rbacService.getIfAvailable();
        if (iRbacService == null) {
            throw new UsernameNotFoundException("Authentication service unavailable");
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
        RbacUserCredentials credentials = iRbacService.loadUserCredentials(identifier, identifierType);
        if (credentials == null) {
            throw new UsernameNotFoundException("User not found");
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
