package com.merryl.ecommerce.notification_service;

import com.merryl.ecommerce.notification.NotificationServiceApplication;
import com.merryl.ecommerce.order.event.OrderItem;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Properties;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EmbeddedKafka(
		partitions = 1,
		topics = "order-placed",
		bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@SpringBootTest(classes = NotificationServiceApplication.class, properties = {
		"spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.consumer.properties.schema.registry.url=mock://notification-service",
		"spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
		"spring.kafka.producer.value-serializer=io.confluent.kafka.serializers.KafkaAvroSerializer",
		"spring.kafka.producer.properties.schema.registry.url=mock://notification-service"
})
class NotificationServiceIntegrationTests {

	@Autowired
	private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

	@MockitoBean
	private JavaMailSender javaMailSender;

	@Test
	void shouldSendOrderConfirmationWhenOrderPlacedEventIsConsumed() throws Exception {
		OrderPlacedEvent event = new OrderPlacedEvent(
				"order-123", "buyer@example.com", "Taylor", "Customer", List.of());
		kafkaTemplate.send("order-placed", "order-123", event).get(10, TimeUnit.SECONDS);

		ArgumentCaptor<MimeMessagePreparator> preparatorCaptor =
				ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(javaMailSender, timeout(10_000)).send(preparatorCaptor.capture());

		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		preparatorCaptor.getValue().prepare(message);

		assertEquals("buyer@example.com", message.getAllRecipients()[0].toString());
		assertTrue(message.getSubject().contains("order-123"));
		assertTrue(message.getContent().toString().contains("Order Number: order-123"));
	}

	@Test
	void shouldIncludeOrderItemsInConfirmationEmail() throws Exception {
		OrderPlacedEvent event = new OrderPlacedEvent(
				"order-multi", "buyer@example.com", "Taylor", "Customer",
				List.of(new OrderItem("IPHONE-15", "799.00", 2),
						new OrderItem("AIRPODS-PRO", "249.00", 1)));
		kafkaTemplate.send("order-placed", "order-multi", event).get(10, TimeUnit.SECONDS);

		ArgumentCaptor<MimeMessagePreparator> preparatorCaptor =
				ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(javaMailSender, timeout(10_000)).send(preparatorCaptor.capture());

		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		preparatorCaptor.getValue().prepare(message);
		String body = message.getContent().toString();
		assertTrue(body.contains("Order Number: order-multi"));
		assertTrue(body.contains("IPHONE-15 x2 @ 799.00"));
		assertTrue(body.contains("AIRPODS-PRO x1 @ 249.00"));
	}
}