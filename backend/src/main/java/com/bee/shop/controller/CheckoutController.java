package com.bee.shop.controller;

import com.bee.shop.dto.*;
import com.bee.shop.service.OrderService;
import com.bee.shop.service.ShippingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orders;
    private final ShippingService shipping;

    public record CustomerEmail(@NotBlank @Email String email) {}

    @PostMapping("/shipping/rates")
    public ShippingQuote rates(@Valid @RequestBody ShippingRateRequest request) {
        return shipping.quote(request);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(request);
    }

    /** Lacak order: butuh nomor order + email pemesan. Pakai POST agar email tidak masuk log URL. */
    @PostMapping("/orders/{orderNumber}/lookup")
    public OrderResponse track(@PathVariable String orderNumber, @Valid @RequestBody CustomerEmail body) {
        return orders.track(orderNumber, body.email());
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public OrderResponse cancel(@PathVariable String orderNumber, @Valid @RequestBody CustomerEmail body) {
        return orders.cancelByCustomer(orderNumber, body.email());
    }
}
