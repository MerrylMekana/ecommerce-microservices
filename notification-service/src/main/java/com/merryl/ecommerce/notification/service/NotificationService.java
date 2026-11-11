package com.merryl.ecommerce.notification.service;

import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j

public class NotificationService {

    private final JavaMailSender javaMailSender;

    @KafkaListener(topics = "order-placed")
    public  void listen(OrderPlacedEvent orderPlacedEvent) {

        log.info("Received order placed event {} for order {} from topic",
                orderPlacedEvent.getEventId(), orderPlacedEvent.getOrderNumber());
        String items = orderPlacedEvent.getItems().stream()
                .map(item -> "%s x%d @ %s".formatted(
                        item.getSkuCode(), item.getQuantity(), item.getUnitPrice()))
                .collect(Collectors.joining("\n"));
        MimeMessagePreparator messagePreparator = mimeMessage -> {
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
            messageHelper.setFrom("shopsphere@gmail.com");
            messageHelper.setTo(orderPlacedEvent.getEmail().toString());
            messageHelper.setSubject(String.format("Order Confirmed – #%s", orderPlacedEvent.getOrderNumber()));
            messageHelper.setText(String.format("""
                    Hi,

                          Thank you for shopping with ShopSphere!

                          We're happy to let you know that your order has been successfully placed.

                          Order Number: %s
                          Items:
                          %s

                          Thank you for choosing ShopSphere!

                          Best regards,
                          ShopSphere

                    """,
                    orderPlacedEvent.getOrderNumber(), items));
        };
        try {
            javaMailSender.send(messagePreparator);
            log.info("Order notification mail sent successfully");
        }catch (MailException e) {
            log.error("Error while sending order notification mail",e);
            throw new RuntimeException("Error while sending order notification mail", e);

        }
    }
}
