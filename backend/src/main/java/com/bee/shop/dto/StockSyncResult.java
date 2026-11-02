package com.bee.shop.dto;

import java.util.List;

public record StockSyncResult(int updated, List<String> unknownSkus) {}
