package com.lrenyi.oauth2.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface TemplateUserService extends UserDetailsService {
    
    UserDetails loadUserByJobNumber(String username) throws UsernameNotFoundException;
}
