package com.bee.shop.service;

import com.bee.shop.config.AppProperties;
import com.bee.shop.dto.CartItem;
import com.bee.shop.dto.ShippingOption;
import com.bee.shop.dto.ShippingQuote;
import com.bee.shop.dto.ShippingRateRequest;
import com.bee.shop.entity.Product;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.integration.shipping.ShippingProvider;
import com.bee.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProvider provider;
    private final ProductRepository products;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public ShippingQuote quote(ShippingRateRequest request) {
        Map<Long, Product> byId = products.findAllById(request.items().stream().map(CartItem::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        int weight = 0;
        for (CartItem item : request.items()) {
            Product p = byId.get(item.productId());
            if (p == null || !p.isActive()) throw new BusinessException("Produk tidak tersedia: " + item.productId());
            weight += p.getWeightGram() * item.quantity();
        }
        return new ShippingQuote(weight, options(request.destinationCity(), request.postalCode(), weight));
    }

    public List<ShippingOption> options(String destinationCity, String postalCode, int weightGram) {
        return provider.quote(props.shipping().originCity(), destinationCity, postalCode, weightGram);
    }

    /** Tarif selalu dihitung ulang di server: fee dari client tidak pernah dipercaya. */
    public ShippingOption resolve(String code, String destinationCity, String postalCode, int weightGram) {
        return options(destinationCity, postalCode, weightGram).stream()
                .filter(o -> o.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Layanan pengiriman tidak tersedia untuk alamat ini"));
    }
}
