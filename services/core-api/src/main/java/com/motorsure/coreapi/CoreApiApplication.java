package com.motorsure.coreapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class CoreApiApplication {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
