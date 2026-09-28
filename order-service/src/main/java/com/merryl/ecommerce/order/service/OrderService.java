package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.client.InventoryClient;
import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public void placeOrder(OrderRequest orderRequest){

        var isProductInStock = inventoryClient.isInStock(orderRequest.skuCode(), orderRequest.quantity());//provide skucode and quauntity from the orderrequest dto
        //map OrderRequest to Order Object
        if(isProductInStock) {
            Order order = new Order();
            order.setOrderNumber(UUID.randomUUID().toString());
            order.setPrice(orderRequest.price());
            order.setSkuCode(orderRequest.skuCode());
            order.setQuantity(orderRequest.quantity());

            //save order to OrderRepository- inject order Repository into order service class
            orderRepository.save(order);
        }else{
            throw new RuntimeException("Product with skuCode " + orderRequest.skuCode() + " is not in stock");
        }

    }
}
