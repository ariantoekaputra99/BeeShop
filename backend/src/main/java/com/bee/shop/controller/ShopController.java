package com.bee.shop.controller;

import com.bee.shop.dto.*;
import com.bee.shop.entity.*;
import com.bee.shop.repository.ProductRepository;
import com.bee.shop.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor @CrossOrigin(origins="${app.cors-origin}") public class ShopController { private final ProductRepository products; private final OrderService orders; private final ShippingService shipping; private final PaymentService payments;
 @GetMapping("/products") public List<Product> products(){return products.findByActiveTrueOrderByNameAsc();}
 @PostMapping("/shipping/rates") public List<ShippingOption> rates(@Valid @RequestBody ShippingRateRequest request){return shipping.options(request);}
 @PostMapping("/checkout") public ResponseEntity<PaymentSession> checkout(@Valid @RequestBody CheckoutRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(orders.checkout(request));}
 @PostMapping("/payments/callback") public ResponseEntity<Void> callback(@RequestParam String orderNumber,@RequestParam String transactionId,@RequestParam String status){payments.callback(orderNumber,transactionId,status);return ResponseEntity.ok().build();}
}
