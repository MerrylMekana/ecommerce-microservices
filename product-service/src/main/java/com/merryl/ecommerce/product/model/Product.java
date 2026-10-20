package com.merryl.ecommerce.product.model;
//To define product as mongodb doc
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;

@Document(value = "product")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Product {
    @Id   //to specify the unique identifier
    private String id;
    String skuCode;
    private String name;
    private BigDecimal price;
    private String description;
}
