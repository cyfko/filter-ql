package io.github.cyfko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main Spring Boot Application for Integration Tests
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "io.github.cyfko")
@EntityScan(basePackages = "io.github.cyfko")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}