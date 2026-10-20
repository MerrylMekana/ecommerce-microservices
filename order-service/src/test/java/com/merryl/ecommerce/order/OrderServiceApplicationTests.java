package com.merryl.ecommerce.order;

import com.merryl.ecommerce.order.stubs.InventoryClientStub;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderItem;
import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.client.InventoryReservationItemRequest;
import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import com.merryl.ecommerce.order.repository.OrderItemRepository;
import com.merryl.ecommerce.order.repository.OrderRepository;
import com.merryl.ecommerce.order.service.OrderIdempotencyService;
import com.merryl.ecommerce.order.service.OrderIdempotencyStatus;
import com.merryl.ecommerce.order.service.OrderPersistenceService;
import com.merryl.ecommerce.order.service.OrderStateTransitionException;
import io.restassured.RestAssured;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
	private OrderIdempotencyService idempotencyService;

	@MockitoBean
	private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

	@ServiceConnection
	static MySQLContainer mysqlContainer= new MySQLContainer("mysql:8.3.0");

	@LocalServerPort
	private Integer port;

	@BeforeEach
	void setup() {
		RestAssured.baseURI="http://localhost";
		RestAssured.port = port;
	}

	static{
		mysqlContainer.start();
	}
	@Test
	void shouldSubmitOrder() {
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
		verify(kafkaTemplate).send(eq("order-placed"), any(OrderPlacedEvent.class));
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
		verify(kafkaTemplate).send(eq("order-placed"), any(OrderPlacedEvent.class));
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
		verify(kafkaTemplate, times(1)).send(eq("order-placed"), any(OrderPlacedEvent.class));
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
		verify(kafkaTemplate, never()).send(eq("order-placed"), any(OrderPlacedEvent.class));
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

		verify(kafkaTemplate, never()).send(eq("order-placed"), any(OrderPlacedEvent.class));
	}

	@Test
	void shouldNotReserveAgainWhenTheSameOrderRequestIsRetried() {
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
		verify(kafkaTemplate, times(1)).send(eq("order-placed"), any(OrderPlacedEvent.class));
	}

	@Test
	void saveAndCompleteCommitsOnlyWhenProcessingTransitionSucceeds() {
		String successfulOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(successfulOrderNumber, OrderIdempotencyStatus.PROCESSING);

		Assertions.assertTrue(orderPersistenceService.saveAndComplete(order(successfulOrderNumber)));
		Assertions.assertEquals(OrderIdempotencyStatus.SUCCEEDED,
				idempotencyRepository.findByOrderNumber(successfulOrderNumber).orElseThrow().getStatus());
		Assertions.assertTrue(orderRepository.findByOrderNumber(successfulOrderNumber).isPresent());

		String losingOrderNumber = UUID.randomUUID().toString();
		saveIdempotency(losingOrderNumber, OrderIdempotencyStatus.SUCCEEDED);

		Assertions.assertThrows(OrderStateTransitionException.class,
				() -> orderPersistenceService.saveAndComplete(order(losingOrderNumber)));
		Assertions.assertTrue(orderRepository.findByOrderNumber(losingOrderNumber).isEmpty());
		Assertions.assertEquals(OrderIdempotencyStatus.SUCCEEDED,
				idempotencyRepository.findByOrderNumber(losingOrderNumber).orElseThrow().getStatus());
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

		Assertions.assertTrue(orderPersistenceService.saveAndComplete(order));

		Order persisted = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
		Assertions.assertEquals(new BigDecimal("1847.00"), persisted.getPrice());
		Assertions.assertEquals(2, orderItemRepository.findAllByOrder_IdOrderBySkuCode(persisted.getId()).size());
		Assertions.assertEquals(new BigDecimal("1598.00"),
				orderItemRepository.findAllByOrder_IdOrderBySkuCode(persisted.getId()).get(1).getLineTotal());
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
}
