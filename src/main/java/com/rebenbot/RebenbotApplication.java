package com.rebenbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RebenbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(RebenbotApplication.class, args);
    }

}
