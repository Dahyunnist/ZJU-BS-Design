package com.zju.imagemanger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableScheduling
@EnableRabbit
@EnableCaching
public class ImageManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImageManagerApplication.class, args);
    }
}

