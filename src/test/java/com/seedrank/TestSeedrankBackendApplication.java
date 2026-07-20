package com.seedrank;

import org.springframework.boot.SpringApplication;

public class TestSeedrankBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(SeedrankBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
