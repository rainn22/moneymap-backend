package com.example.moneymap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoneymapBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneymapBackendApplication.class, args);
    }

}
