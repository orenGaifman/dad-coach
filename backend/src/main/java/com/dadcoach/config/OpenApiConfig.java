package com.dadcoach.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public OpenAPI dadCoachOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dad Coach API")
                        .version("0.1.0")
                        .description("Dad Coach backend API"));
    }
}
