package com.merryl.ecommerce.order.config;

import com.merryl.ecommerce.order.client.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    //can use @ConfigurationProperties if we have multiple properties to bind to the class. @value is used for a single property
    @Value("${inventory.url}")
    private String inventoryServiceUrl;

    @Value("${inventory.oauth2.registration-id:inventory-service}")
    private String inventoryClientRegistrationId;

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository registrations,
            org.springframework.security.oauth2.client.OAuth2AuthorizedClientService authorizedClients) {
        var manager = new org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, authorizedClients);
        manager.setAuthorizedClientProvider(
                org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    @Bean
    //binding the rest client created to the http interface created by @GetExchange in interface InventoryClient.java
    public InventoryClient inventoryClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.clone()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(getClientRequestFactory())
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizeRequest authorization = OAuth2AuthorizeRequest
                            .withClientRegistrationId(inventoryClientRegistrationId)
                            .principal("order-service")
                            .build();
                    OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorization);
                    if (client == null) {
                        throw new IllegalStateException("Order Service could not obtain Inventory credentials");
                    }
                    request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                    return execution.execute(request, body);
                })
                .build();
        var restClientAdapter = RestClientAdapter.create(restClient);
        var httpServiceProxyFactory = HttpServiceProxyFactory.builderFor(restClientAdapter).build();
        return httpServiceProxyFactory.createClient(InventoryClient.class);
    }

    private ClientHttpRequestFactory getClientRequestFactory() {

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(3));

        return ClientHttpRequestFactoryBuilder
                .detect()
                .build(settings);
    }
}
