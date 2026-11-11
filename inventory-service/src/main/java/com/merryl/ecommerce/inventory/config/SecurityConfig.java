package com.merryl.ecommerce.inventory.config;

import com.merryl.ecommerce.inventory.security.KeycloakRealmRoleAuthoritiesConverter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain inventorySecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**",
                                "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/inventory/stock", "/api/inventory/stock/**").hasRole("ADMIN")
                        .requestMatchers("/api/inventory/reservations", "/api/inventory/reservations/**")
                        .hasRole("ORDER_SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/inventory").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleAuthoritiesConverter());
        return converter;
    }
}
