package com.bee.shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="shop_order_items") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) private Order order; @ManyToOne(fetch=FetchType.LAZY,optional=false) private Product product; @Column(nullable=false) private Integer quantity; @Column(nullable=false,precision=19,scale=2) private BigDecimal unitPrice; @Column(nullable=false,precision=19,scale=2) private BigDecimal lineTotal; }
