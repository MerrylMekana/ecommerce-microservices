package com.merryl.ecommerce.product;

import com.merryl.ecommerce.product.model.Product;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mongodb.MongoDBContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests {
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.5");

    @LocalServerPort
    private Integer port;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    static {
        mongoDBContainer.start();
    }

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", "CUSTOMER"));
    }

    @Test
    void shouldCreateProduct() {
        String skuCode = "TEST-" + UUID.randomUUID();
        String requestBody = productJson(skuCode, "iphone 15", 1000);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer admin-token")
                .body(requestBody)
                .when()
                .post("/api/product")
                .then()
                .statusCode(201)
                .body("id", Matchers.notNullValue())
                .body("name", Matchers.equalTo("iphone 15"))
                .body("skuCode", Matchers.equalTo(skuCode))
                .body("description", Matchers.equalTo("Example product"))
                .body("price", Matchers.equalTo(1000));
    }

    @Test
    void rejectsMissingSkuNonPositivePriceAndDuplicateSku() {
        String missingSku = """
                {"name":"Phone","description":"Example","price":10}
                """;
        RestAssured.given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer admin-token").body(missingSku)
                .when().post("/api/product").then().statusCode(400);

        String zeroPrice = productJson("SKU-" + UUID.randomUUID(), "Phone", 0);
        RestAssured.given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer admin-token").body(zeroPrice)
                .when().post("/api/product").then().statusCode(400);

        String skuCode = "DUP-" + UUID.randomUUID();
        String valid = productJson(skuCode, "Phone", 10);
        RestAssured.given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer admin-token").body(valid)
                .when().post("/api/product").then().statusCode(201);
        RestAssured.given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer admin-token").body(valid)
                .when().post("/api/product").then().statusCode(409)
                .body("detail", Matchers.not(Matchers.containsString("E11000")));
    }

    @Test
    void productCreationRequiresAdminAndDoesNotRestrictProductListing() throws Exception {
        String body = productJson("AUTH-" + UUID.randomUUID(), "Phone", 10);

        RestAssured.given().contentType(ContentType.JSON).body(body)
                .when().post("/api/product").then().statusCode(401);
        RestAssured.given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer user-token").body(body)
                .when().post("/api/product").then().statusCode(403);
        HttpResponse<String> productList = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/product"))
                        .header("Authorization", "Bearer user-token").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, productList.statusCode());
    }

    @Test
    void productSkuIndexEnforcesCaseInsensitiveDatabaseUniqueness() {
        String skuCode = "INDEX-" + UUID.randomUUID();
        assertTrue(mongoTemplate.indexOps(Product.class).getIndexInfo().stream()
                .anyMatch(index -> "uq_product_sku_code".equals(index.getName())));

        mongoTemplate.insert(Product.builder().skuCode(skuCode).name("First").build());
        assertThrows(DuplicateKeyException.class, () -> mongoTemplate.insert(
                Product.builder().skuCode(skuCode.toLowerCase()).name("Duplicate").build()));
    }

    @Test
    void concurrentProductRequestsCannotCreateDuplicateSkus() throws Exception {
        String skuCode = "RACE-" + UUID.randomUUID();
        String body = productJson(skuCode, "Phone", 10);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/product"))
                    .header("Authorization", "Bearer admin-token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var first = executor.submit(() -> {
                start.await();
                return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            });
            var second = executor.submit(() -> {
                start.await();
                return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            });
            start.countDown();
            List<Integer> statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1L, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1L, statuses.stream().filter(status -> status == 409).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private static String productJson(String skuCode, String name, int price) {
        return """
                {"skuCode":"%s","name":"%s","description":"Example product","price":%d}
                """.formatted(skuCode, name, price);
    }

    private static Jwt jwt(String token, String role) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }
}
