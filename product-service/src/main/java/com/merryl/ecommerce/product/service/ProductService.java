package com.merryl.ecommerce.product.service;

import com.merryl.ecommerce.product.dto.ProductRequest;
import com.merryl.ecommerce.product.dto.ProductResponse;
import com.merryl.ecommerce.product.model.Product;
import com.merryl.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    //don't have to write getters and setters for records like we do in class
    public ProductResponse createProduct(ProductRequest productRequest) {
        validate(productRequest);
        String skuCode = productRequest.skuCode().trim();
        if (productRepository.existsBySkuCodeIgnoreCase(skuCode)) {
            throw new ProductConflictException("A product with SKU " + skuCode + " already exists");
        }
        Product product = Product.builder()
                .skuCode(skuCode)
                .name(productRequest.name())
                .description(productRequest.description())
                .price(productRequest.price())
                .build();
        productRepository.save(product);
        log.info("Product {} created successfully", product.getId());
        return new ProductResponse(product.getId(), product.getSkuCode(), product.getName(), product.getDescription(), product.getPrice());
    }

    public List<ProductResponse> getAllProducts() {
        //map list of product objects to productResponse
        return productRepository.findAll()
                .stream()
                .map(product -> new ProductResponse(product.getId(), product.getSkuCode(), product.getName(), product.getDescription(), product.getPrice()))
                .toList();
    }

    private void validate(ProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("product request is required");
        }
        if (request.skuCode() == null || request.skuCode().isBlank()) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
    }
}
