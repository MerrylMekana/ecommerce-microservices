package com.merryl.ecommerce.order;

import com.merryl.ecommerce.order.stubs.InventoryClientStub;
import com.merryl.ecommerce.order.event.OrderPlacedItemPayload;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderItem;
import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.client.InventoryReservationItemRequest;
import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import com.merryl.ecommerce.order.repository.OrderItemRepository;
import com.merryl.ecommerce.order.repository.OrderOutboxRepository;
import com.merryl.ecommerce.order.repository.OrderRepository;
import com.merryl.ecommerce.order.service.OrderIdempotencyService;
import com.merryl.ecommerce.order.service.OrderIdempotencyStatus;
import com.merryl.ecommerce.order.service.OrderPersistenceService;
import com.merryl.ecommerce.order.service.OrderStateTransitionException;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"order.outbox.poll-interval-ms=60000",
				"management.endpoints.web.exposure.include=health,info,prometheus",
				"spring.security.oauth2.client.registration.inventory-service.client-id=order-service",
				"spring.security.oauth2.client.registration.inventory-service.client-secret=test-secret",
				"spring.security.oauth2.client.registration.inventory-service.authorization-grant-type=client_credentials",
				"spring.security.oauth2.client.registration.inventory-service.provider=keycloak",
				"spring.security.oauth2.client.provider.keycloak.token-uri=http://keycloak/token"
		})
@EnableWireMock(
		@ConfigureWireMock(port = 0)
)
class OrderServiceApplicationTests {

	@Autowired
	private OrderPersistenceService orderPersistenceService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private OrderIdempotencyRepository idempotencyRepository;

	@Autowired
	private OrderOutboxRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private OrderIdempotencyService idempotencyService;

	@MockitoBean
	private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OAuth2AuthorizedClientManager authorizedClientManager;

	@ServiceConnection
	static MySQLContainer mysqlContainer= new MySQLContainer("mysql:8.3.0");

	@LocalServerPort
	private Integer port;

	@BeforeEach
	void setup() {
		RestAssured.baseURI="http://localhost";
		RestAssured.port = port;
		RestAssured.requestSpecification = new RequestSpecBuilder().build();
		RestAssured.requestSpecification.header("Authorization", "Bearer customer-token");
		RestAssured.requestSpecification = new RequestSpecBuilder()
				.addHeader("Authorization", "Bearer customer-token")
				.build();
		RestAssured.requestSpecification = new RequestSpecBuilder().build();
		RestAssured.requestSpecification.header("Authorization", "Bearer customer-token");
		when(jwtDecoder.decode("customer-token")).thenReturn(jwt("customer-token", "CUSTOMER"));
		when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
		ClientRegistration registration = ClientRegistration.withRegistrationId("inventory-service")
				.clientId("order-service")
				.clientSecret("test-secret")
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri("http://keycloak/token")
				.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER, "order-service-token",
				Instant.now(), Instant.now().plusSeconds(300), Set.of());
		when(authorizedClientManager.authorize(any())).thenReturn(
				new OAuth2AuthorizedClient(registration, "order-service", accessToken));
	}

	static{
		mysqlContainer.start();
	}
	@Test
	void shouldSubmitOrder() throws Exception {
		double createdBefore = meterRegistry.counter("orders_created_count").count();
		double succeededBefore = meterRegistry.counter("orders_succeeded").count();
		String submitOrderJson= """
				{
				    "skuCode": "iphone 15",
				    "quantity": 1,
				    "price": 1000,
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";

		InventoryClientStub.stubInventoryCall("iphone 15", 1);
		var responseBodyStr = RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-success-001")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.log().all()
				.statusCode(201)
				.extract().body().asString();

		assertThat(responseBodyStr, Matchers.is("Order Placed Successfully"));
		Assertions.assertEquals(createdBefore + 1, meterRegistry.counter("orders_created_count").count());
		Assertions.assertEquals(succeededBefore + 1, meterRegistry.counter("orders_succeeded").count());
		assertOutboxCreatedFor("order-success-001");

		HttpResponse<String> prometheus = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/prometheus"))
						.GET().build(),
				HttpResponse.BodyHandlers.ofString());
		Assertions.assertEquals(200, prometheus.statusCode(), prometheus.body());
		Assertions.assertTrue(prometheus.body().contains("orders_created_count_total"), prometheus.body());
	}

	@Test
	void directOrderEndpointRequiresAUserTokenButAllowsAuthenticatedCustomersAndAdmins() throws Exception {
		HttpResponse<String> unauthenticated = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/order"))
						.header("Content-Type", "application/json")
						.header("Idempotency-Key", "order-unauthenticated")
						.POST(HttpRequest.BodyPublishers.ofString("{}"))
						.build(), HttpResponse.BodyHandlers.ofString());
		Assertions.assertEquals(401, unauthenticated.statusCode());

		InventoryClientStub.stubInventoryCall("admin-auth-sku", 1);
		RestAssured.given()
				.header("Authorization", "Bearer admin-token")
				.contentType("application/json")
				.header("Idempotency-Key", "order-admin-auth")
				.body("""
						{"skuCode":"admin-auth-sku","quantity":1,"price":10,
						 "userDetails":{"firstName":"Admin","lastName":"User","email":"admin@example.com"}}
						""")
				.when().post("/api/order").then().statusCode(201);
	}

	@Test
	void shouldSubmitMultiItemOrderWithOneInventoryBatchReservation() {
		String submitOrderJson = """
				{
				    "items": [
				        {"skuCode": "IPHONE-15", "price": 799, "quantity": 2},
				        {"skuCode": "AIRPODS-PRO", "price": 249, "quantity": 1}
				    ],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubBatchInventoryCall(List.of(
				new InventoryReservationItemRequest("AIRPODS-PRO", 1),
				new InventoryReservationItemRequest("IPHONE-15", 2)),
				"RESERVED", "RESERVED", "Inventory reserved");

		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-multi-001")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.statusCode(201)
				.body(Matchers.equalTo("Order Placed Successfully"));

		InventoryClientStub.verifyBatchReservationCalls(1, "AIRPODS-PRO");
		Order order = orderRepository.findAll().stream()
				.filter(saved -> orderItemRepository.findAllByOrder_IdOrderBySkuCode(saved.getId()).size() == 2)
				.filter(saved -> new BigDecimal("1847.00").compareTo(saved.getPrice()) == 0)
				.findFirst().orElseThrow();
		Assertions.assertEquals(2, orderItemRepository.findAllByOrder_IdOrderBySkuCode(order.getId()).size());
		assertOutboxCreatedFor("order-multi-001");
	}

	@Test
	void shouldRejectDuplicateSkuLines() {
		String submitOrderJson = """
				{
				    "items": [
				        {"skuCode": "DUPLICATE-SKU", "price": 10, "quantity": 1},
				        {"skuCode": "DUPLICATE-SKU", "price": 10, "quantity": 2}
				    ],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";

		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-duplicate-001")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.statusCode(400);
	}

	@Test
	void newOneItemListRequestStillUsesExistingSuccessResponse() {
		String submitOrderJson = """
				{
				    "items": [{"skuCode": "ONE-ITEM-SKU", "price": 25.50, "quantity": 3}],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubInventoryCall("ONE-ITEM-SKU", 3);

		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-one-item-list")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.statusCode(201)
				.body(Matchers.equalTo("Order Placed Successfully"));

		InventoryClientStub.verifyReservationCalls(1, "ONE-ITEM-SKU");
	}

	@Test
	void reorderedMultiItemRetryReusesTheSameIdempotencyResult() {
		String orderedItems = """
				{
				    "items": [
				        {"skuCode": "IPHONE-15", "price": 799, "quantity": 1},
				        {"skuCode": "AIRPODS-PRO", "price": 249, "quantity": 1}
				    ],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		String reorderedItems = """
				{
				    "items": [
				        {"skuCode": "AIRPODS-PRO", "price": 249.00, "quantity": 1},
				        {"skuCode": "IPHONE-15", "price": 799.00, "quantity": 1}
				    ],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubBatchInventoryCall(List.of(
				new InventoryReservationItemRequest("AIRPODS-PRO", 1),
				new InventoryReservationItemRequest("IPHONE-15", 1)),
				"RESERVED", "RESERVED", "Inventory reserved");

		for (String body : List.of(orderedItems, reorderedItems)) {
			RestAssured.given()
					.contentType("application/json")
					.header("Idempotency-Key", "order-reordered-multi")
					.body(body)
					.when()
					.post("/api/order")
					.then()
					.statusCode(201)
					.body(Matchers.equalTo("Order Placed Successfully"));
		}

		InventoryClientStub.verifyBatchReservationCalls(1, "AIRPODS-PRO");
		assertOutboxCreatedFor("order-reordered-multi");
	}

	@Test
	void multiItemInsufficientInventoryDoesNotPersistOrPublishOrder() {
		String submitOrderJson = """
				{
				    "items": [
				        {"skuCode": "AIRPODS-PRO", "price": 249, "quantity": 1},
				        {"skuCode": "OUT-OF-STOCK-SKU", "price": 99, "quantity": 10}
				    ],
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubBatchInventoryCall(List.of(
				new InventoryReservationItemRequest("AIRPODS-PRO", 1),
				new InventoryReservationItemRequest("OUT-OF-STOCK-SKU", 10)),
				"REJECTED", "INSUFFICIENT_INVENTORY", "Insufficient inventory");

		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-multi-insufficient")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.statusCode(409)
				.body("code", Matchers.equalTo("INSUFFICIENT_INVENTORY"));

		String orderNumber = idempotencyRepository.findByIdempotencyKey("order-multi-insufficient")
				.orElseThrow().getOrderNumber();
		Assertions.assertTrue(orderRepository.findByOrderNumber(orderNumber).isEmpty());
		assertOutboxNotCreatedFor("order-multi-insufficient");
	}

	@Test
	void shouldReturnConflictWhenInventoryIsInsufficient() {
		String submitOrderJson = """
				{
				    "skuCode": "out-of-stock-test",
				    "quantity": 2,
				    "price": 1000,
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubInsufficientInventory("out-of-stock-test", 2);

		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-insufficient-001")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.statusCode(409)
				.body("code", Matchers.equalTo("INSUFFICIENT_INVENTORY"));

		assertOutboxNotCreatedFor("order-insufficient-001");
	}

	@Test
	void shouldNotReserveAgainWhenTheSameOrderRequestIsRetried() {
		double replaysBefore = meterRegistry.counter("orders_idempotency_replays").count();
		String submitOrderJson = """
				{
				    "skuCode": "idempotent-test",
				    "quantity": 1,
				    "price": 1000,
				    "userDetails": {
				        "firstName": "Taylor",
				        "lastName": "Customer",
				        "email": "buyer@example.com"
				    }
				}
				""";
		InventoryClientStub.stubInventoryCall("idempotent-test", 1);
		for (int attempt = 0; attempt < 2; attempt++) {
			RestAssured.given()
					.contentType("application/json")
					.header("Idempotency-Key", "order-idempotent-001")
					.body(submitOrderJson)
					.when()
					.post("/api/order")
					.then()
					.statusCode(201);
		}

		String differentRequest = submitOrderJson.replace("\"quantity\": 1", "\"quantity\": 2");
		RestAssured.given()
				.contentType("application/json")
				.header("Idempotency-Key", "order-idempotent-001")
				.body(differentRequest)
				.when()
				.post("/api/order")
				.then()
				.statusCode(409)
				.body("code", Matchers.equalTo("IDEMPOTENCY_KEY_REUSED"));

		InventoryClientStub.verifyReservationCalls(1, "idempotent-test");
		assertOutboxCreatedFor("order-idempotent-001");
		Assertions.assertEquals(replaysBefore + 1,
				meterRegistry.counter("orders_idempotency_replays").count());
	}

	@Test
	void saveAndCompleteCommitsOnlyWhenProcessingTransitionSucceeds() {
		String successfulOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(successfulOrderNumber, OrderIdempotencyStatus.PROCESSING);

		orderPersistenceService.saveAndComplete(order(successfulOrderNumber), eventPayload(successfulOrderNumber));
		Assertions.assertEquals(OrderIdempotencyStatus.SUCCEEDED,
				idempotencyRepository.findByOrderNumber(successfulOrderNumber).orElseThrow().getStatus());
		Assertions.assertTrue(orderRepository.findByOrderNumber(successfulOrderNumber).isPresent());
		Assertions.assertEquals(1, outboxRepository.countByEventTypeAndAggregateId(
				"ORDER_PLACED", successfulOrderNumber));

		String losingOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(losingOrderNumber, OrderIdempotencyStatus.SUCCEEDED);

		Assertions.assertThrows(OrderStateTransitionException.class,
				() -> orderPersistenceService.saveAndComplete(
						order(losingOrderNumber), eventPayload(losingOrderNumber)));
		Assertions.assertTrue(orderRepository.findByOrderNumber(losingOrderNumber).isEmpty());
		Assertions.assertEquals(OrderIdempotencyStatus.SUCCEEDED,
				idempotencyRepository.findByOrderNumber(losingOrderNumber).orElseThrow().getStatus());
		Assertions.assertEquals(0, outboxRepository.countByEventTypeAndAggregateId(
				"ORDER_PLACED", losingOrderNumber));
	}

	@Test
	void orderIdempotencyAndOutboxRollBackTogether() {
		String orderNumber = UUID.randomUUID().toString();
		saveIdempotency(orderNumber, OrderIdempotencyStatus.PROCESSING);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		Assertions.assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
			orderPersistenceService.saveAndComplete(order(orderNumber), eventPayload(orderNumber));
			throw new IllegalStateException("force transaction rollback");
		}));

		Assertions.assertTrue(orderRepository.findByOrderNumber(orderNumber).isEmpty());
		Assertions.assertEquals(OrderIdempotencyStatus.PROCESSING,
				idempotencyRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus());
		Assertions.assertEquals(0, outboxRepository.countByEventTypeAndAggregateId("ORDER_PLACED", orderNumber));
	}

	@Test
	void compensationTransitionReportsWinnerAndLoser() {
		String pendingOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(pendingOrderNumber, OrderIdempotencyStatus.PROCESSING);
		Assertions.assertTrue(idempotencyService.markCompensationPending(pendingOrderNumber, "failed"));

		String succeededOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(succeededOrderNumber, OrderIdempotencyStatus.SUCCEEDED);
		Assertions.assertFalse(idempotencyService.markCompensationPending(succeededOrderNumber, "failed"));
		Assertions.assertEquals(OrderIdempotencyStatus.SUCCEEDED,
				idempotencyRepository.findByOrderNumber(succeededOrderNumber).orElseThrow().getStatus());
	}

	@Test
	void persistsOrderItemsAndAggregateTotalTogether() {
		String orderNumber = UUID.randomUUID().toString();
		saveIdempotency(orderNumber, OrderIdempotencyStatus.PROCESSING);
		Order order = new Order();
		order.setOrderNumber(orderNumber);
		order.setPrice(new BigDecimal("1847.00"));

		OrderItem first = new OrderItem();
		first.setSkuCode("IPHONE-15");
		first.setQuantity(2);
		first.setUnitPrice(new BigDecimal("799.00"));
		first.setLineTotal(new BigDecimal("1598.00"));
		order.addItem(first);

		OrderItem second = new OrderItem();
		second.setSkuCode("AIRPODS-PRO");
		second.setQuantity(1);
		second.setUnitPrice(new BigDecimal("249.00"));
		second.setLineTotal(new BigDecimal("249.00"));
		order.addItem(second);

		orderPersistenceService.saveAndComplete(order, eventPayload(orderNumber));

		Order persisted = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
		Assertions.assertEquals(new BigDecimal("1847.00"), persisted.getPrice());
		Assertions.assertEquals(2, orderItemRepository.findAllByOrder_IdOrderBySkuCode(persisted.getId()).size());
		Assertions.assertEquals(new BigDecimal("1598.00"),
				orderItemRepository.findAllByOrder_IdOrderBySkuCode(persisted.getId()).get(1).getLineTotal());
		Assertions.assertEquals(1, outboxRepository.countByEventTypeAndAggregateId("ORDER_PLACED", orderNumber));
	}

	private void assertOutboxCreatedFor(String idempotencyKey) {
		String orderNumber = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow().getOrderNumber();
		Assertions.assertEquals(1, outboxRepository.countByEventTypeAndAggregateId("ORDER_PLACED", orderNumber));
	}

	private void assertOutboxNotCreatedFor(String idempotencyKey) {
		String orderNumber = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow().getOrderNumber();
		Assertions.assertEquals(0, outboxRepository.countByEventTypeAndAggregateId("ORDER_PLACED", orderNumber));
	}

	private OrderPlacedPayload eventPayload(String orderNumber) {
		return new OrderPlacedPayload(
				UUID.randomUUID().toString(),
				orderNumber,
				"buyer@example.com",
				"Taylor",
				"Customer",
				List.of(new OrderPlacedItemPayload("test-sku", "12", 1)));
	}

	private void saveIdempotency(String orderNumber, OrderIdempotencyStatus status) {
		OrderIdempotency idempotency = new OrderIdempotency();
		idempotency.setIdempotencyKey("test-" + UUID.randomUUID());
		idempotency.setRequestHash("test-hash");
		idempotency.setOrderNumber(orderNumber);
		idempotency.setStatus(status);
		idempotencyRepository.saveAndFlush(idempotency);
	}

	private Order order(String orderNumber) {
		Order order = new Order();
		order.setOrderNumber(orderNumber);
		order.setSkuCode("test-sku");
		order.setPrice(new BigDecimal("12.00"));
		order.setQuantity(1);
		return order;
	}

	private static Jwt jwt(String token, String role) {
		return Jwt.withTokenValue(token)
				.header("alg", "none")
				.claim("realm_access", Map.of("roles", List.of(role)))
				.build();
	}
}
