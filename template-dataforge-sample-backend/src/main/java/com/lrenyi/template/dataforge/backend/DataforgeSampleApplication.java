package com.lrenyi.template.dataforge.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.lrenyi.template.dataforge.backend")
public class DataforgeSampleApplication {
    
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(DataforgeSampleApplication.class, args);
    }
}
