package com.merryl.ecommerce.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"product.service.url=http://localhost:1",
		"order.service.url=http://localhost:1",
		"inventory.service.url=http://localhost:1"
})
class ApiGatewayApplicationTests {

	@LocalServerPort
	private int port;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void setupJwtDecoder() {
		when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
		when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", "CUSTOMER"));
	}

	@Test
	void adminRoutesRejectAnonymousAndNonAdminUsersButPermitAdmins() throws Exception {
		String productUrl = "http://localhost:" + port + "/api/product";
		assertEquals(401, send(productUrl, null).statusCode());
		assertEquals(403, send(productUrl, "user-token").statusCode());
		int adminProductStatus = send(productUrl, "admin-token").statusCode();
		assertNotEquals(401, adminProductStatus);
		assertNotEquals(403, adminProductStatus);

		String inventoryUrl = "http://localhost:" + port + "/api/inventory/stock";
		assertEquals(401, send(inventoryUrl, null).statusCode());
		assertEquals(403, send(inventoryUrl, "user-token").statusCode());
		int adminInventoryStatus = send(inventoryUrl, "admin-token").statusCode();
		assertNotEquals(401, adminInventoryStatus);
		assertNotEquals(403, adminInventoryStatus);
	}

	@Test
	void customerProductReadsAndOrderRequestsRemainAvailableToAuthenticatedUsers() throws Exception {
		HttpResponse<String> productRead = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/product"))
						.header("Authorization", "Bearer user-token").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertNotEquals(401, productRead.statusCode());
		assertNotEquals(403, productRead.statusCode());

		String orderUrl = "http://localhost:" + port + "/api/order";
		assertNotEquals(401, send(orderUrl, "user-token").statusCode());
		assertNotEquals(403, send(orderUrl, "user-token").statusCode());
		assertNotEquals(401, send(orderUrl, "admin-token").statusCode());
		assertNotEquals(403, send(orderUrl, "admin-token").statusCode());
	}

	private HttpResponse<String> send(String url, String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}"));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static Jwt jwt(String token, String role) {
		return Jwt.withTokenValue(token)
				.header("alg", "none")
				.claim("realm_access", Map.of("roles", List.of(role)))
				.build();
	}
}
