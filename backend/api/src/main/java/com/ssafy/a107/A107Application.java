package com.ssafy.a107;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class A107Application {

    public static void main(String[] args) {
        SpringApplication.run(A107Application.class, args);
    }
}
