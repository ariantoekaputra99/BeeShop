package com.bee.shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "shop_order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(length = 500)
    private String note;

    /** Siapa yang mengubah: customer, system, payment:midtrans, admin:&lt;username&gt;. */
    @Column(length = 80)
    private String actor;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}
