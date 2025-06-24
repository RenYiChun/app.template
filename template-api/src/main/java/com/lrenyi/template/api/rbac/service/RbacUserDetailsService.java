package com.lrenyi.template.api.rbac.service;

import com.lrenyi.template.api.rbac.model.IdentifierType;
import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;
import com.lrenyi.template.api.rbac.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RbacUserDetailsService implements UserDetailsService {
    
    private final IRbacService rbacService;
    
    public RbacUserDetailsService(IRbacService rbacService) {
        this.rbacService = rbacService;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
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
        User user = rbacService.findUserByIdentifier(identifier, identifierType);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with identifier: " + identifier);
        }
        
        List<Role> roles = rbacService.getRolesByUserId(user.getId());
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            authorities.addAll(roles.stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                                    .toList());
            List<Permission> permissions = roles.stream()
                                                .flatMap(role -> rbacService.getPermissionsByRoleId(role.getId())
                                                                            .stream())
                                                .toList();
            authorities.addAll(permissions.stream().map(p -> new SimpleGrantedAuthority(p.getPermission())).toList());
        }
        
        return new org.springframework.security.core.userdetails.User(user.getUsername(),
                                                                      user.getPassword(),
                                                                      authorities
        );
    }
}