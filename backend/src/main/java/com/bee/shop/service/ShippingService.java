package com.bee.shop.service;

import com.bee.shop.config.AppProperties;
import com.bee.shop.dto.CartItem;
import com.bee.shop.dto.ShippingOption;
import com.bee.shop.dto.ShippingQuote;
import com.bee.shop.dto.ShippingRateRequest;
import com.bee.shop.entity.Product;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.integration.shipping.ShipmentRequest;
import com.bee.shop.integration.shipping.ShippingProvider;
import com.bee.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShippingService {

    private static final int MAX_CACHE_ENTRIES = 5_000;

    private record CacheEntry(List<ShippingOption> options, Instant expiresAt) {}

    private final ShippingProvider provider;
    private final ProductRepository products;
    private final Duration cacheTtl;
    private final Map<ShipmentRequest, CacheEntry> cache = new ConcurrentHashMap<>();

    public ShippingService(ShippingProvider provider, ProductRepository products, AppProperties props) {
        this.provider = provider;
        this.products = products;
        this.cacheTtl = Duration.ofMinutes(Math.max(0, props.shipping().cacheTtlMinutes()));
    }

    @Transactional(readOnly = true)
    public ShippingQuote quote(ShippingRateRequest request) {
        Map<Long, Product> byId = products.findAllById(request.items().stream().map(CartItem::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        int weight = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (CartItem item : request.items()) {
            Product p = byId.get(item.productId());
            if (p == null || !p.isActive()) throw new BusinessException("Produk tidak tersedia: " + item.productId());
            weight += p.getWeightGram() * item.quantity();
            value = value.add(p.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        List<ShippingOption> options = options(new ShipmentRequest(request.destinationCity().trim(), request.postalCode(), weight, value));
        if (options.isEmpty()) throw new BusinessException("Belum ada layanan pengiriman ke alamat ini");
        return new ShippingQuote(weight, options);
    }

    /** Tarif selalu dihitung ulang di server: fee dari client tidak pernah dipercaya. */
    public ShippingOption resolve(String code, ShipmentRequest shipment) {
        return options(shipment).stream()
                .filter(o -> o.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Layanan pengiriman tidak tersedia untuk alamat ini. Silakan cek ongkir ulang."));
    }

    List<ShippingOption> options(ShipmentRequest shipment) {
        Instant now = Instant.now();
        CacheEntry hit = cache.get(shipment);
        if (hit != null && hit.expiresAt().isAfter(now)) return hit.options();

        List<ShippingOption> fresh = provider.quote(shipment);
        if (!cacheTtl.isZero() && !fresh.isEmpty()) {
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
            if (cache.size() < MAX_CACHE_ENTRIES) cache.put(shipment, new CacheEntry(fresh, now.plus(cacheTtl)));
        }
        return fresh;
    }
}
