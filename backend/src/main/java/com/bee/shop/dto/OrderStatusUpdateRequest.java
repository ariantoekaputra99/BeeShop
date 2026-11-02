package com.bee.shop.dto;

import com.bee.shop.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderStatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 64) String trackingNumber,
                                       @Size(max = 500) String note) {}
