package dev.jordy.jordylab;

import org.springframework.boot.SpringApplication;

public class TestJordylabApplication {

    static void main(String[] args) {
        SpringApplication.from(JordylabApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
