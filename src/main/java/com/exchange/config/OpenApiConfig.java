package com.exchange.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI exchangeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Trading Exchange API")
                .version("1.0.0")
                .description("A concurrent spot trading exchange: accounts, a price-time-priority "
                        + "matching engine, and live market data over REST + WebSocket."));
    }
}
