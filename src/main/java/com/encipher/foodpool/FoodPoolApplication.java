package com.encipher.foodpool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodPoolApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodPoolApplication.class, args);
    }
}
