package com.merryl.ecommerce.notification_service;

import com.merryl.ecommerce.notification.NotificationServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.boot.test.context.SpringBootTest;

@EmbeddedKafka(
		partitions = 1,
		topics = "order-placed",
		bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@SpringBootTest(classes = NotificationServiceApplication.class, properties = {
		"spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.consumer.properties.schema.registry.url=mock://notification-service",
		"spring.kafka.producer.properties.schema.registry.url=mock://notification-service"
})
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
