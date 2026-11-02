package com.merryl.ecommerce.inventory;

import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationItemRequest;
import com.merryl.ecommerce.inventory.dto.InventoryStockRequest;
import com.merryl.ecommerce.inventory.model.Inventory;
import com.merryl.ecommerce.inventory.repository.InventoryRepository;
import com.merryl.ecommerce.inventory.service.InventoryService;
import com.merryl.ecommerce.inventory.service.InventoryConflictException;
import com.merryl.ecommerce.inventory.service.InsufficientStockException;
import com.merryl.ecommerce.inventory.service.ReservationConflictException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;

//@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryServiceApplicationTests {

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private InventoryRepository inventoryRepository;

	@ServiceConnection
	static MySQLContainer mySQLContainer= new MySQLContainer("mysql:8.3.0");

	@LocalServerPort
	private Integer port;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	static {
		mySQLContainer.start();
	}

	@BeforeEach
	void setupJwtDecoder() {
		when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
		when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", "CUSTOMER"));
	}

	@Test
	void shouldReadInventory() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(
				"http://localhost:" + port + "/api/inventory?skuCode=IPHONE-15&quantity=10")).GET().build(),
				BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertTrue(Boolean.parseBoolean(response.body()));

		HttpResponse<String> negativeResponse = client.send(HttpRequest.newBuilder(URI.create(
				"http://localhost:" + port + "/api/inventory?skuCode=IPHONE-15&quantity=10000")).GET().build(),
				BodyHandlers.ofString());
		assertEquals(200, negativeResponse.statusCode());
		assertFalse(Boolean.parseBoolean(negativeResponse.body()));

	}

	@Test
	void repeatedReservationAndReleaseDoNotChangeStockTwice() {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 3));
		var request = new InventoryReservationRequest("reservation-" + UUID.randomUUID(), skuCode, 2);

		var first = inventoryService.reserve(request);
		var retry = inventoryService.reserve(request);

		assertEquals("RESERVED", first.status());
		assertEquals("RESERVED", retry.status());
		assertEquals(1, inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity());

		assertEquals("RELEASED", inventoryService.release(request.reservationId()).status());
		assertEquals("RELEASED", inventoryService.release(request.reservationId()).status());
		assertEquals(3, inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity());
	}

	@Test
	void concurrentReservationsCannotSellTheLastUnitTwice() throws Exception {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 1));
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<java.util.concurrent.Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				String reservationId = "race-" + UUID.randomUUID();
				results.add(executor.submit(() -> {
					start.await();
					return inventoryService.reserve(new InventoryReservationRequest(reservationId, skuCode, 1))
							.status();
				}));
			}
			start.countDown();

			var statuses = List.of(results.get(0).get(10, TimeUnit.SECONDS),
					results.get(1).get(10, TimeUnit.SECONDS));
			assertEquals(1, statuses.stream().filter("RESERVED"::equals).count());
			assertEquals(1, statuses.stream().filter("REJECTED"::equals).count());
			assertEquals(0, inventoryRepository.findAll().stream()
					.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
					.findFirst().orElseThrow().getQuantity());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void manualRemovalAndOrderReservationCannotBothConsumeTheSameUnit() throws Exception {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 1));
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var remove = executor.submit(() -> {
				start.await();
				try {
					inventoryService.removeStock(skuCode, 1);
					return "MANUAL_REMOVAL";
				} catch (InsufficientStockException exception) {
					return "MANUAL_REJECTED";
				}
			});
			var reserve = executor.submit(() -> {
				start.await();
				return inventoryService.reserve(new InventoryReservationRequest(
						"manual-race-" + UUID.randomUUID(), skuCode, 1)).status();
			});
			start.countDown();

			var outcomes = List.of(remove.get(10, TimeUnit.SECONDS), reserve.get(10, TimeUnit.SECONDS));
			assertEquals(1L, outcomes.stream().filter("MANUAL_REMOVAL"::equals).count()
					+ outcomes.stream().filter("RESERVED"::equals).count());
			assertEquals(0, quantity(skuCode));
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void reservationIdCannotBeReusedForDifferentRequest() {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 5));
		String reservationId = "mismatch-" + UUID.randomUUID();
		inventoryService.reserve(new InventoryReservationRequest(reservationId, skuCode, 1));

		Assertions.assertThrows(ReservationConflictException.class,
				() -> inventoryService.reserve(new InventoryReservationRequest(reservationId, skuCode, 2)));
		assertEquals(4, inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity());
	}

	@Test
	void missingSkuIsRejectedWithoutChangingInventory() {
		var response = inventoryService.reserve(new InventoryReservationRequest(
				"missing-" + UUID.randomUUID(), uniqueSku(), 1));

		assertEquals("REJECTED", response.status());
		assertEquals("SKU_NOT_FOUND", response.code());
	}

	@Test
	void insufficientReservationDoesNotChangeStockAndIsIdempotent() {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 1));
		var request = new InventoryReservationRequest("short-" + UUID.randomUUID(), skuCode, 2);

		var first = inventoryService.reserve(request);
		var retry = inventoryService.reserve(request);

		assertEquals("REJECTED", first.status());
		assertEquals("INSUFFICIENT_INVENTORY", first.code());
		assertEquals(first, retry);
		assertEquals(1, inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity());
	}

	@Test
	void batchReservationReservesAllItemsAndRepeatedReleaseRestoresAllItemsOnce() {
		String firstSku = uniqueSku();
		String secondSku = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, firstSku, 5));
		inventoryRepository.saveAndFlush(new Inventory(null, secondSku, 3));
		String reservationId = "batch-" + UUID.randomUUID();
		var request = new InventoryReservationRequest(reservationId, List.of(
				new InventoryReservationItemRequest(firstSku, 2),
				new InventoryReservationItemRequest(secondSku, 1)));

		var reserved = inventoryService.reserve(request);
		var retried = inventoryService.reserve(request);

		assertEquals("RESERVED", reserved.status());
		assertEquals(reserved, retried);
		assertEquals(3, quantity(firstSku));
		assertEquals(2, quantity(secondSku));

		assertEquals("RELEASED", inventoryService.release(reservationId).status());
		assertEquals("RELEASED", inventoryService.release(reservationId).status());
		assertEquals(5, quantity(firstSku));
		assertEquals(3, quantity(secondSku));
	}

	@Test
	void batchReservationWithInsufficientStockDoesNotDecrementAnySku() {
		String availableSku = uniqueSku();
		String shortSku = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, availableSku, 5));
		inventoryRepository.saveAndFlush(new Inventory(null, shortSku, 1));
		var request = new InventoryReservationRequest("batch-short-" + UUID.randomUUID(), List.of(
				new InventoryReservationItemRequest(availableSku, 2),
				new InventoryReservationItemRequest(shortSku, 2)));

		var result = inventoryService.reserve(request);

		assertEquals("REJECTED", result.status());
		assertEquals("INSUFFICIENT_INVENTORY", result.code());
		assertEquals(5, quantity(availableSku));
		assertEquals(1, quantity(shortSku));
		assertEquals(result, inventoryService.reserve(request));
	}

	@Test
	void batchReservationWithMissingSkuDoesNotDecrementExistingSku() {
		String existingSku = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, existingSku, 5));
		var request = new InventoryReservationRequest("batch-missing-" + UUID.randomUUID(), List.of(
				new InventoryReservationItemRequest(existingSku, 2),
				new InventoryReservationItemRequest(uniqueSku(), 1)));

		var result = inventoryService.reserve(request);

		assertEquals("REJECTED", result.status());
		assertEquals("SKU_NOT_FOUND", result.code());
		assertEquals(5, quantity(existingSku));
	}

	@Test
	void batchReservationRejectsDuplicateSkusAndInvalidQuantities() {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 5));
		Assertions.assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(
				new InventoryReservationRequest("duplicate-" + UUID.randomUUID(), List.of(
						new InventoryReservationItemRequest(skuCode, 1),
						new InventoryReservationItemRequest(skuCode, 2)))));
		Assertions.assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(
				new InventoryReservationRequest("duplicate-case-" + UUID.randomUUID(), List.of(
						new InventoryReservationItemRequest(skuCode.toLowerCase(java.util.Locale.ROOT), 1),
						new InventoryReservationItemRequest(skuCode, 2)))));
		Assertions.assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(
				new InventoryReservationRequest("invalid-batch-" + UUID.randomUUID(), List.of(
						new InventoryReservationItemRequest(skuCode, 0)))));
		assertEquals(5, quantity(skuCode));
	}

	@Test
	void sameBatchReservationIdCannotBeReusedForDifferentItems() {
		String firstSku = uniqueSku();
		String secondSku = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, firstSku, 5));
		inventoryRepository.saveAndFlush(new Inventory(null, secondSku, 5));
		String reservationId = "batch-mismatch-" + UUID.randomUUID();
		inventoryService.reserve(new InventoryReservationRequest(reservationId, List.of(
				new InventoryReservationItemRequest(firstSku, 1),
				new InventoryReservationItemRequest(secondSku, 1))));

		Assertions.assertThrows(ReservationConflictException.class, () -> inventoryService.reserve(
				new InventoryReservationRequest(reservationId, List.of(
						new InventoryReservationItemRequest(firstSku, 2),
						new InventoryReservationItemRequest(secondSku, 1)))));
		assertEquals(4, quantity(firstSku));
		assertEquals(4, quantity(secondSku));
	}

	@Test
	void concurrentBatchReservationsCannotOversellOrPartiallyReserveOtherItems() throws Exception {
		String sharedSku = uniqueSku();
		String abundantSku = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, sharedSku, 1));
		inventoryRepository.saveAndFlush(new Inventory(null, abundantSku, 10));
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<java.util.concurrent.Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				String reservationId = "batch-race-" + UUID.randomUUID();
				results.add(executor.submit(() -> {
					start.await();
					return inventoryService.reserve(new InventoryReservationRequest(reservationId, List.of(
									new InventoryReservationItemRequest(sharedSku, 1),
									new InventoryReservationItemRequest(abundantSku, 1))))
							.status();
				}));
			}
			start.countDown();

			var statuses = List.of(results.get(0).get(10, TimeUnit.SECONDS),
					results.get(1).get(10, TimeUnit.SECONDS));
			assertEquals(1, statuses.stream().filter("RESERVED"::equals).count());
			assertEquals(1, statuses.stream().filter("REJECTED"::equals).count());
			assertEquals(0, quantity(sharedSku));
			assertEquals(9, quantity(abundantSku));
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void batchReservationIsAvailableThroughHttp() throws Exception {
		String firstSku = uniqueSku();
		String secondSku = uniqueSku();
		String reservationId = "batch-http-" + UUID.randomUUID();
		inventoryRepository.saveAndFlush(new Inventory(null, firstSku, 3));
		inventoryRepository.saveAndFlush(new Inventory(null, secondSku, 2));
		String body = """
				{"reservationId":"%s","items":[
				    {"skuCode":"%s","quantity":2},
				    {"skuCode":"%s","quantity":1}
				]}
				""".formatted(reservationId, firstSku, secondSku);

		HttpResponse<String> reserveResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
						"http://localhost:" + port + "/api/inventory/reservations"))
				.header("Content-Type", "application/json")
				.POST(BodyPublishers.ofString(body))
				.build(), BodyHandlers.ofString());

		assertEquals(200, reserveResponse.statusCode());
		assertTrue(reserveResponse.body().contains("\"status\":\"RESERVED\""));
		assertEquals(1, quantity(firstSku));
		assertEquals(1, quantity(secondSku));
	}

	@Test
	void rejectsNonPositiveReservationQuantity() {
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> inventoryService.reserve(new InventoryReservationRequest("invalid", uniqueSku(), 0)));
	}

	@Test
	void initializesStockIncludingZeroAndRepeatingSameRequestDoesNotResetStock() {
		String skuCode = uniqueSku();
		var initialized = inventoryService.initializeStock(new InventoryStockRequest(skuCode, 0));
		assertEquals(0, initialized.quantity());
		assertEquals(initialized, inventoryService.initializeStock(new InventoryStockRequest(skuCode, 0)));

		inventoryService.addStock(skuCode, 4);
		Assertions.assertThrows(InventoryConflictException.class,
				() -> inventoryService.initializeStock(new InventoryStockRequest(skuCode, 0)));
		assertEquals(4, quantity(skuCode));
	}

	@Test
	void manualAdjustmentsArePositiveAndCannotMakeInventoryNegative() {
		String skuCode = uniqueSku();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 5));

		assertEquals(8, inventoryService.addStock(skuCode, 3).quantity());
		assertEquals(6, inventoryService.removeStock(skuCode, 2).quantity());
		Assertions.assertThrows(InsufficientStockException.class,
				() -> inventoryService.removeStock(skuCode, 7));
		assertEquals(6, quantity(skuCode));

		Assertions.assertThrows(IllegalArgumentException.class,
				() -> inventoryService.addStock(skuCode, 0));
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> inventoryService.removeStock(skuCode, -1));
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> inventoryService.initializeStock(new InventoryStockRequest(uniqueSku(), -1)));
	}

	@Test
	void stockManagementEndpointsReturnInventoryAndMeaningfulStatuses() throws Exception {
		String skuCode = uniqueSku();
		HttpClient client = HttpClient.newHttpClient();
		String baseUrl = "http://localhost:" + port + "/api/inventory/stock";

		HttpResponse<String> initialize = client.send(HttpRequest.newBuilder(URI.create(baseUrl))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer admin-token")
				.POST(BodyPublishers.ofString("""
						{"skuCode":"%s","quantity":2}
						""".formatted(skuCode)))
				.build(), BodyHandlers.ofString());
		assertEquals(201, initialize.statusCode());
		assertTrue(initialize.body().contains("\"quantity\":2"));

		HttpResponse<String> add = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/" + skuCode + "/add"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer admin-token")
				.POST(BodyPublishers.ofString("{\"quantity\":3}"))
				.build(), BodyHandlers.ofString());
		assertEquals(200, add.statusCode());
		assertTrue(add.body().contains("\"quantity\":5"));

		HttpResponse<String> excessiveRemove = client.send(
				HttpRequest.newBuilder(URI.create(baseUrl + "/" + skuCode + "/remove"))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer admin-token")
						.POST(BodyPublishers.ofString("{\"quantity\":6}"))
						.build(), BodyHandlers.ofString());
		assertEquals(409, excessiveRemove.statusCode());
		assertEquals(5, quantity(skuCode));

		HttpResponse<String> invalidQuantity = client.send(
				HttpRequest.newBuilder(URI.create(baseUrl + "/" + skuCode + "/remove"))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer admin-token")
						.POST(BodyPublishers.ofString("{\"quantity\":0}"))
						.build(), BodyHandlers.ofString());
		assertEquals(400, invalidQuantity.statusCode());
		assertEquals(5, quantity(skuCode));

		HttpResponse<String> stockList = client.send(
				HttpRequest.newBuilder(URI.create(baseUrl))
						.header("Authorization", "Bearer admin-token").GET().build(), BodyHandlers.ofString());
		assertEquals(200, stockList.statusCode());
		assertTrue(stockList.body().contains("\"skuCode\":\"" + skuCode + "\""));
	}

	@Test
	void stockManagementEndpointsRequireAdmin() throws Exception {
		String baseUrl = "http://localhost:" + port + "/api/inventory/stock";
		String body = "{\"skuCode\":\"" + uniqueSku() + "\",\"quantity\":2}";

		HttpResponse<String> unauthenticated = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(baseUrl)).header("Content-Type", "application/json")
						.POST(BodyPublishers.ofString(body)).build(), BodyHandlers.ofString());
		assertEquals(401, unauthenticated.statusCode());

		HttpResponse<String> forbidden = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(baseUrl)).header("Content-Type", "application/json")
						.header("Authorization", "Bearer user-token")
						.POST(BodyPublishers.ofString(body)).build(), BodyHandlers.ofString());
		assertEquals(403, forbidden.statusCode());

		HttpResponse<String> customerReservation = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/inventory/reservations"))
						.header("Content-Type", "application/json")
						.POST(BodyPublishers.ofString("""
								{"reservationId":"customer-checkout","skuCode":"%s","quantity":1}
								""".formatted(uniqueSku())))
						.build(), BodyHandlers.ofString());
		assertEquals(200, customerReservation.statusCode());
	}

	@Test
	void reservationAndReleaseAreAvailableThroughHttp() throws Exception {
		String skuCode = uniqueSku();
		String reservationId = "http-" + UUID.randomUUID();
		inventoryRepository.saveAndFlush(new Inventory(null, skuCode, 2));
		String body = """
				{"reservationId":"%s","skuCode":"%s","quantity":1}
				""".formatted(reservationId, skuCode);
		HttpClient client = HttpClient.newHttpClient();

		HttpResponse<String> reserveResponse = client.send(HttpRequest.newBuilder(URI.create(
						"http://localhost:" + port + "/api/inventory/reservations"))
				.header("Content-Type", "application/json")
				.POST(BodyPublishers.ofString(body))
				.build(), BodyHandlers.ofString());
		assertEquals(200, reserveResponse.statusCode());
		assertTrue(reserveResponse.body().contains("\"status\":\"RESERVED\""));

		HttpResponse<String> releaseResponse = client.send(HttpRequest.newBuilder(URI.create(
						"http://localhost:" + port + "/api/inventory/reservations/" + reservationId + "/release"))
				.POST(BodyPublishers.noBody())
				.build(), BodyHandlers.ofString());
		assertEquals(200, releaseResponse.statusCode());
		assertTrue(releaseResponse.body().contains("\"status\":\"RELEASED\""));
		assertEquals(2, inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity());
	}

	private String uniqueSku() {
		return "TEST-" + UUID.randomUUID();
	}

	private static Jwt jwt(String token, String role) {
		return Jwt.withTokenValue(token)
				.header("alg", "none")
				.claim("realm_access", Map.of("roles", List.of(role)))
				.build();
	}

	private int quantity(String skuCode) {
		return inventoryRepository.findAll().stream()
				.filter(inventory -> skuCode.equals(inventory.getSkuCode()))
				.findFirst().orElseThrow().getQuantity();
	}

}
