package com.lrenyi.template.platform.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.lrenyi.template.platform.backend")
public class PlatformSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformSampleApplication.class, args);
    }
}
