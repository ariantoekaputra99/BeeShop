package com.bee.shop.service;

import com.bee.shop.entity.*;
import com.bee.shop.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Satu-satunya tempat perubahan status order: validasi transisi, timestamp, histori, pengembalian stok. */
@Component
public class OrderLifecycle {

    public void transition(Order order, OrderStatus next, String note) {
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new BusinessException("Status tidak dapat diubah dari " + current + " ke " + next);
        }
        OffsetDateTime now = OffsetDateTime.now();
        switch (next) {
            case PAID -> order.setPaidAt(now);
            case SHIPPED -> order.setShippedAt(now);
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED, REFUNDED -> {
                order.setCancelledAt(now);
                if (current.holdsStock()) restoreStock(order);
            }
            default -> { }
        }
        order.setStatus(next);
        order.addHistory(next, note);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            p.setStock(p.getStock() + item.getQuantity());
        }
    }
}
