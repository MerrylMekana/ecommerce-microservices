package com.merryl.ecommerce.product;

import com.merryl.ecommerce.product.model.Product;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.mongodb.MongoDBContainer;

//test containers will spin up a docker instance of what we want to test.. in this case, an actual mongodb instance
//@Import(TestcontainersConfiguration.class)
// when we run application, the application is opened on port 8080 and test shouldnt coincide with that.. so random port
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests {
	//springboot will automatically inject the uri details for mongodb container.. no need to define in separate application.properties file
    @ServiceConnection
	static MongoDBContainer mongoDBContainer=new MongoDBContainer("mongo:7.0.5");

    //injects whatever port the application is running on into the variable
	@LocalServerPort
	private Integer port;

	@BeforeEach
	void setup() {
		RestAssured.baseURI="http://localhost";
		RestAssured.port = port;
	}

	static {
		mongoDBContainer.start();
	}

	@Test
	void shouldCreateProduct() {
		//multiline string introduced in java 14
		String requestBody= """
				{
				    "name": "iphone 15",
				    "description": "Apple smartphone 15th version",
				    "price": 1000
				}""";
		RestAssured.given()
				.contentType(ContentType.JSON)
				.body(requestBody)
				.when()
				.post("/api/product")
				.then()
				.statusCode(201)
				.body("id", Matchers.notNullValue())
				.body("name", Matchers.equalTo("iphone 15"))
				.body("description", Matchers.equalTo("Apple smartphone 15th version"))
				.body("price", Matchers.equalTo(1000));

	}

}
