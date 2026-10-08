package com.pawpasta.glowscan_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlowScanBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlowScanBeApplication.class, args);
    }

}
