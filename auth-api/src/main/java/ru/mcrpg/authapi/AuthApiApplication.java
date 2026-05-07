package ru.mcrpg.authapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.mcrpg.authapi.config.AuthApiProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthApiProperties.class)
public class AuthApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApiApplication.class, args);
    }
}
