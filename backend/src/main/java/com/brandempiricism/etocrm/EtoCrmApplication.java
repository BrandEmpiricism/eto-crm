package com.brandempiricism.etocrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication
public class EtoCrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(EtoCrmApplication.class, args);
    }
}

