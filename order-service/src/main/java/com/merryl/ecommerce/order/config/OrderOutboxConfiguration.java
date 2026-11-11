package com.merryl.ecommerce.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderOutboxConfiguration {

    @Bean
    ObjectMapper outboxObjectMapper() {
        return new ObjectMapper();
    }
}
