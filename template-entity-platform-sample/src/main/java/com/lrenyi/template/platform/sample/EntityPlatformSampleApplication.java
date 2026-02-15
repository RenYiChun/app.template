package com.lrenyi.template.platform.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {"com.lrenyi.template.platform.sample", "com.lrenyi.template.platform.domain"})
public class EntityPlatformSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityPlatformSampleApplication.class, args);
    }
}
