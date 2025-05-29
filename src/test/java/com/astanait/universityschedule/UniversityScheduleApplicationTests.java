package com.astanait.universityschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
public class UniversityScheduleApplicationTests {
    public static void main(String[] args) {
        SpringApplication.run(UniversityScheduleApplication.class, args);
    }
}