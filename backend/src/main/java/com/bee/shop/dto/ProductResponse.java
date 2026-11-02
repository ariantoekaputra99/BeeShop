package com.bee.shop.dto;

import com.bee.shop.entity.Product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String sku, BigDecimal price, int stock, int weightGram,
                              String category, String imageUrl, String description, boolean active) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getPrice(), p.getStock(), p.getWeightGram(),
                p.getCategory(), p.getImageUrl(), p.getDescription(), p.isActive());
    }
}
