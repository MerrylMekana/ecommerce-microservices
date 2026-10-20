package com.merryl.ecommerce.order.repository;

import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.service.OrderIdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderIdempotency> findByOrderNumber(String orderNumber);

    @Modifying
    @Transactional
    @Query("""
            update OrderIdempotency request
            set request.status = :newStatus,
                request.errorCode = :errorCode,
                request.errorMessage = :errorMessage
            where request.orderNumber = :orderNumber
              and request.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("orderNumber") String orderNumber,
            @Param("expectedStatus") OrderIdempotencyStatus expectedStatus,
            @Param("newStatus") OrderIdempotencyStatus newStatus,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);
}
