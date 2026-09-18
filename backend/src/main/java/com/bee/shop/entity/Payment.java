package com.bee.shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity @Table(name="shop_payments") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @OneToOne(optional=false) private Order order; @Column(nullable=false) private String provider; private String transactionId; @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Enumerated(EnumType.STRING) private PaymentStatus status; private OffsetDateTime paidAt; }
