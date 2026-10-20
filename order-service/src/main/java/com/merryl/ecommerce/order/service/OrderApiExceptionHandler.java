package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.client.InventoryUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderApiExceptionHandler {

    @ExceptionHandler(InsufficientInventoryException.class)
    ProblemDetail handleInventoryRejection(InsufficientInventoryException exception) {
        HttpStatus status = "SKU_NOT_FOUND".equals(exception.getCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.CONFLICT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", exception.getCode());
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", "IDEMPOTENCY_KEY_REUSED");
        return problem;
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    ProblemDetail handleInventoryUnavailable(InventoryUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setProperty("code", "INVENTORY_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail handleInventoryHttpFailure(RestClientException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Inventory Service request failed");
        problem.setProperty("code", "INVENTORY_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(CompensationPendingException.class)
    ProblemDetail handleCompensationPending(CompensationPendingException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setProperty("code", "INVENTORY_RELEASE_PENDING");
        return problem;
    }

    @ExceptionHandler(OrderStateTransitionException.class)
    ProblemDetail handleOrderStateTransitionLost(OrderStateTransitionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setProperty("code", "ORDER_STATE_TRANSITION_LOST");
        return problem;
    }

    @ExceptionHandler(OrderCreationException.class)
    ProblemDetail handleOrderCreationFailure(OrderCreationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
        problem.setProperty("code", "ORDER_CREATION_FAILED");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
