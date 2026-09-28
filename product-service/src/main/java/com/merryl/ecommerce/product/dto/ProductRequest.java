package com.merryl.ecommerce.product.dto;

import java.math.BigDecimal;

public record ProductRequest(String id,String name,String description, BigDecimal price) {

}
