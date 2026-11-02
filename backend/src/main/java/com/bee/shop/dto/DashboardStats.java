package com.bee.shop.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardStats(BigDecimal revenue, Map<String, Long> ordersByStatus, long activeProducts,
                             long lowStockProducts) {}
