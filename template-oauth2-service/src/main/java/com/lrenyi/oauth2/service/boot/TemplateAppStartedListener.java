package com.lrenyi.oauth2.service.boot;

import static com.lrenyi.oauth2.service.oauth2.password.LoginNameUserDetailService.ALL_LOGIN_NAME_TYPE;

import com.lrenyi.oauth2.service.oauth2.password.LoginNameUserDetailService;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TemplateAppStartedListener implements ApplicationListener<ApplicationStartedEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        Map<String, LoginNameUserDetailService> beans =
                event.getApplicationContext().getBeansOfType(LoginNameUserDetailService.class);
        beans.forEach((s, loginNameUserDetailService) -> ALL_LOGIN_NAME_TYPE.put(
                loginNameUserDetailService.loginNameType(),
                loginNameUserDetailService
        ));
        ServiceLoader<LoginNameUserDetailService> loader =
                ServiceLoader.load(LoginNameUserDetailService.class);
        for (LoginNameUserDetailService detailService : loader) {
            String loginNameType = detailService.loginNameType();
            ALL_LOGIN_NAME_TYPE.putIfAbsent(loginNameType, detailService);
        }
    }
}
