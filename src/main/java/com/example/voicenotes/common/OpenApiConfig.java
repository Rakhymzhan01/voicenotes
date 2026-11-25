package com.example.voicenotes.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI voicenotesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Voicenotes API")
                .version("0.1.0")
                .description("Screen + voice recordings to structured notes (kk / ru / en / mixed)."));
    }
}
