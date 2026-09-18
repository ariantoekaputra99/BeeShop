package com.bee.shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="shop_orders") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String orderNumber; @Column(nullable=false) private String customerName; @Column(nullable=false) private String customerEmail; @Column(nullable=false) private String address; @Enumerated(EnumType.STRING) @Column(nullable=false) private OrderStatus status; @Column(nullable=false,precision=19,scale=2) private BigDecimal subtotal; @Column(nullable=false,precision=19,scale=2) private BigDecimal shippingFee; @Column(nullable=false,precision=19,scale=2) private BigDecimal total; private String courier; private String trackingNumber; private OffsetDateTime createdAt; @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) @Builder.Default private List<OrderItem> items=new ArrayList<>(); public void addItem(OrderItem item){items.add(item);item.setOrder(this);} }
