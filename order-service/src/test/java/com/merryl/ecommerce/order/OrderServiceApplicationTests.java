package com.merryl.ecommerce.order;

import com.merryl.ecommerce.order.stubs.InventoryClientStub;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.util.regex.Matcher;

import static org.hamcrest.MatcherAssert.assertThat;
//tests should not connect to external service.. have to mock the method that calls external service. can use either mockito or wiremock
//wiremomck actually mocks the api and we can test the http intercation while mockito just mocks the method response
//order service calls wiremock server and it will stub the call to the inventory service.. we will provide stub and mock response for isInStock api
//have to set up a stub so the wiremoc server can answer the request from the order service
//@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock(
		@ConfigureWireMock(port = 0)
)
class OrderServiceApplicationTests {

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
				    "price": 1000
				}
				""";

		InventoryClientStub.stubInventoryCall("iphone 15", 1);
		var responseBodyStr = RestAssured.given()
				.contentType("application/json")
				.body(submitOrderJson)
				.when()
				.post("/api/order")
				.then()
				.log().all()
				.statusCode(201)
				.extract().body().asString();

		assertThat(responseBodyStr, Matchers.is("Order Placed Successfully"));

	}

}
