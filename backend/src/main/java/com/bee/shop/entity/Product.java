package com.bee.shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="shop_products") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private String name; @Column(nullable=false,unique=true) private String sku; @Column(nullable=false,precision=19,scale=2) private BigDecimal price; @Column(nullable=false) private Integer stock; private String category; private String imageUrl; private String description; private boolean active=true; }
