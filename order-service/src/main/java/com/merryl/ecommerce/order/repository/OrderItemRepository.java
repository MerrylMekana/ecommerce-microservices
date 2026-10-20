package com.merryl.ecommerce.order.repository;

import com.merryl.ecommerce.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_IdOrderBySkuCode(Long orderId);
}
