package com.lrenyi.template.fastgen.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fastgen 示例应用启动类。
 * 启动后访问: http://localhost:8080/api/user
 */
@SpringBootApplication(scanBasePackages = {"com.lrenyi.template.fastgen.example", "com.lrenyi.user", "com.lrenyi.page"})
@MapperScan("com.lrenyi.user")
public class FastgenExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastgenExampleApplication.class, args);
        System.out.println("\n==============================================");
        System.out.println("Fastgen Example 启动成功！");
        System.out.println("----------------------------------------------");
        System.out.println("  前端首页:  http://localhost:8080/");
        System.out.println("  登录页:    http://localhost:8080/#/login");
        System.out.println("  用户管理:  http://localhost:8080/#/user");
        System.out.println("  API 地址:  http://localhost:8080/api/user");
        System.out.println("----------------------------------------------");
        System.out.println("  H2 Console: http://localhost:8080/h2-console");
        System.out.println("  JDBC URL:   jdbc:h2:mem:testdb");
        System.out.println("  用户名:    sa  密码: (留空)");
        System.out.println("==============================================\n");
    }
}
