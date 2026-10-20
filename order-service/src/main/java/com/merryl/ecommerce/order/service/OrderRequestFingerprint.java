package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.dto.OrderItemRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public final class OrderRequestFingerprint {

    private OrderRequestFingerprint() {
    }

    public static String from(OrderRequest request) {
        var user = request.userDetails();
        List<String> values = new ArrayList<>();
        for (OrderItemRequest item : request.canonicalItems()) {
            values.add(item.skuCode());
            values.add(item.price().stripTrailingZeros().toPlainString());
            values.add(item.quantity().toString());
        }
        values.add(user.firstName());
        values.add(user.lastName());
        values.add(user.email());
        String canonical = values.stream()
                .map(value -> value.length() + ":" + value)
                .collect(Collectors.joining());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
