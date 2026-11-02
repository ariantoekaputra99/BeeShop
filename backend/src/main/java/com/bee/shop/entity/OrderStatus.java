package com.bee.shop.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PROCESSING, REFUNDED),
            PROCESSING, EnumSet.of(SHIPPED, REFUNDED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class)
    );

    public boolean canTransitionTo(OrderStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    /** Status yang stoknya masih "dipegang" order dan harus dikembalikan bila dibatalkan/refund. */
    public boolean holdsStock() {
        return this == PENDING_PAYMENT || this == PAID || this == PROCESSING;
    }
}
