package com.dadcoach;

import com.dadcoach.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WhatsAppProperties.class)
public class DadCoachApplication {
  public static void main(String[] args) { SpringApplication.run(DadCoachApplication.class, args); }
}
