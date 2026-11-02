package com.bee.shop.scheduler;

import com.bee.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.order", name = "expiry-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {

    private final OrderService orders;

    @Scheduled(fixedDelayString = "${app.order.expiry-check-interval-ms:60000}", initialDelay = 30_000)
    public void expireUnpaidOrders() {
        int n = orders.expireOverdueOrders();
        if (n > 0) log.info("{} order kedaluwarsa dibatalkan, stok dikembalikan", n);
    }
}
