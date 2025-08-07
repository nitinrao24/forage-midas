package com.jpmc.midascore;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.logging.Logger;

@SpringBootApplication
public class MidasCoreApplication {

    private static final Logger LOGGER = Logger.getLogger(MidasCoreApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication.run(MidasCoreApplication.class, args);
    }

    // Simple sanity bean so the context is non-trivial and we can observe startup
    @Bean
    public CommandLineRunner startupChecker() {
        return args -> LOGGER.info("MidasCoreApplication context initialized successfully.");
    }
}
