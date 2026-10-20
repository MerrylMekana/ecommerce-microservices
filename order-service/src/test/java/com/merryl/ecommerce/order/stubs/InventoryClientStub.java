package com.merryl.ecommerce.order.stubs;

import com.merryl.ecommerce.order.client.InventoryReservationItemRequest;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class InventoryClientStub {

    public static void stubInventoryCall(String skuCode, Integer quantity) {
        stubReservation(skuCode, quantity, "RESERVED", "RESERVED", "Inventory reserved");
    }

    public static void stubInsufficientInventory(String skuCode, Integer quantity) {
        stubReservation(skuCode, quantity, "REJECTED",
                "INSUFFICIENT_INVENTORY", "Insufficient inventory for SKU " + skuCode);
    }

    public static void stubBatchInventoryCall(
            List<InventoryReservationItemRequest> items, String status, String code, String message) {
        InventoryReservationItemRequest firstItem = items.getFirst();
        stubFor(post(urlPathEqualTo("/api/inventory/reservations"))
                .withRequestBody(matchingJsonPath("$.items[0].skuCode", equalTo(firstItem.skuCode())))
                .withRequestBody(matchingJsonPath("$.items[0].quantity", equalTo(firstItem.quantity().toString())))
                .willReturn(okJson("""
                        {
                          "reservationId": "wiremock-reservation",
                          "status": "%s",
                          "code": "%s",
                          "message": "%s"
                        }
                        """.formatted(status, code, message))));
    }

    public static void stubRelease(String reservationId) {
        stubFor(post(urlPathEqualTo("/api/inventory/reservations/" + reservationId + "/release"))
                .willReturn(okJson("""
                        {
                          "reservationId": "%s",
                          "status": "RELEASED",
                          "code": "RELEASED",
                          "message": "Inventory reservation released"
                        }
                        """.formatted(reservationId))));
    }

    public static void verifyReservationCalls(int count, String skuCode) {
        verify(count, postRequestedFor(urlPathEqualTo("/api/inventory/reservations"))
                .withRequestBody(matchingJsonPath("$.skuCode", equalTo(skuCode))));
    }

    public static void verifyBatchReservationCalls(int count, String firstSkuCode) {
        verify(count, postRequestedFor(urlPathEqualTo("/api/inventory/reservations"))
                .withRequestBody(matchingJsonPath("$.items[0].skuCode", equalTo(firstSkuCode))));
    }

    private static void stubReservation(
            String skuCode, Integer quantity, String status, String code, String message) {
        stubFor(post(urlPathEqualTo("/api/inventory/reservations"))
                .withRequestBody(matchingJsonPath("$.skuCode", equalTo(skuCode)))
                .withRequestBody(matchingJsonPath("$.quantity", equalTo(quantity.toString())))
                .willReturn(okJson("""
                        {
                          "reservationId": "wiremock-reservation",
                          "status": "%s",
                          "code": "%s",
                          "message": "%s"
                        }
                        """.formatted(status, code, message))));
    }
}
