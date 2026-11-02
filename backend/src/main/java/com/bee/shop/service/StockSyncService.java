package com.bee.shop.service;

import com.bee.shop.dto.SalesExport;
import com.bee.shop.dto.StockSyncRequest;
import com.bee.shop.dto.StockSyncResult;
import com.bee.shop.entity.Product;
import com.bee.shop.repository.OrderRepository;
import com.bee.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sinkronisasi dengan BeePos-App:
 * - push stok: BeePos mengirim stok absolut per SKU ke BeeShop
 * - pull penjualan: BeePos menarik penjualan online yang sudah dibayar sejak waktu tertentu
 */
@Service
@RequiredArgsConstructor
public class StockSyncService {

    private final ProductRepository products;
    private final OrderRepository orders;

    @Transactional
    public StockSyncResult syncStock(StockSyncRequest request) {
        Map<String, Integer> stockBySku = new LinkedHashMap<>();
        request.items().forEach(i -> stockBySku.put(i.sku().trim(), i.stock()));

        Map<String, Product> found = products.findBySkuIn(stockBySku.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        found.forEach((sku, p) -> p.setStock(stockBySku.get(sku)));

        List<String> unknown = stockBySku.keySet().stream().filter(s -> !found.containsKey(s)).toList();
        return new StockSyncResult(found.size(), unknown);
    }

    @Transactional(readOnly = true)
    public List<SalesExport> salesSince(OffsetDateTime since) {
        return orders.findPaidSince(since).stream()
                .map(o -> new SalesExport(o.getOrderNumber(), o.getStatus().name(), o.getPaidAt(), o.getTotal(),
                        o.getItems().stream().map(i -> new SalesExport.Line(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList()))
                .toList();
    }
}
