package com.merryl.ecommerce.product.controller;

import com.merryl.ecommerce.product.service.ProductConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidProduct(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ProductConflictException.class)
    ProblemDetail handleDuplicateSku(ProductConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDatabaseDuplicateSku(DuplicateKeyException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A product with this SKU already exists");
    }
}
