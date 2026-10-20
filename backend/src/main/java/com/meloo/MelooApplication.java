package com.meloo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MelooApplication {

  public static void main(String[] args) {
    SpringApplication.run(MelooApplication.class, args);
  }
}
