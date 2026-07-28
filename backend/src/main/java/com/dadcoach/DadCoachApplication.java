package com.dadcoach;

import com.dadcoach.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(WhatsAppProperties.class)
@EnableScheduling
public class DadCoachApplication {
  public static void main(String[] args) { SpringApplication.run(DadCoachApplication.class, args); }
}
