package com.merryl.ecommerce.notification_service;

import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderEventSchemaCompatibilityTests {

    @Test
    void newReaderUsesDefaultItemsFieldWhenReadingHistoricalEventSchema() throws Exception {
        Schema historicalSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "OrderPlacedEvent",
                  "namespace": "com.merryl.ecommerce.order.event",
                  "fields": [
                    {"name": "orderNumber", "type": "string"},
                    {"name": "email", "type": "string"},
                    {"name": "firstName", "type": "string"},
                    {"name": "lastName", "type": "string"}
                  ]
                }
                """);
        GenericData.Record historicalEvent = new GenericData.Record(historicalSchema);
        historicalEvent.put("orderNumber", "legacy-order");
        historicalEvent.put("email", "buyer@example.com");
        historicalEvent.put("firstName", "Taylor");
        historicalEvent.put("lastName", "Customer");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new GenericDatumWriter<GenericData.Record>(historicalSchema).write(historicalEvent, encoder);
        encoder.flush();

        var reader = new SpecificDatumReader<OrderPlacedEvent>(
                historicalSchema, OrderPlacedEvent.getClassSchema());
        OrderPlacedEvent decoded = reader.read(null,
                DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null));

        assertEquals("legacy-order", decoded.getOrderNumber().toString());
        assertEquals("buyer@example.com", decoded.getEmail().toString());
        assertEquals(0, decoded.getItems().size());
        assertEquals("", decoded.getEventId().toString());
    }
}
