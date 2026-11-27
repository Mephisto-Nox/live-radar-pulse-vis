package com.github.mephisto_nox.radar_pulse_vis.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.github.mephisto_nox.radar_pulse_vis")
public class PulseVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseVisionApplication.class, args);
    }
}