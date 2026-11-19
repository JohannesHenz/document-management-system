package com.swen3.dms.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OcrWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrWorkerApplication.class, args);
    }
}
