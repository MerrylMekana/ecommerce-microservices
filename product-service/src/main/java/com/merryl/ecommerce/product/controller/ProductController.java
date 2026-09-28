package com.merryl.ecommerce.product.controller;

import com.merryl.ecommerce.product.dto.ProductRequest;
import com.merryl.ecommerce.product.dto.ProductResponse;
import com.merryl.ecommerce.product.model.Product;
import com.merryl.ecommerce.product.repository.ProductRepository;
import com.merryl.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    //injecting product service into controller
    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    //returning product to see info of the product created
    public ProductResponse createProduct(@RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }
}
