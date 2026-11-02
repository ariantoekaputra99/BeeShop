package com.bee.shop.service;

import com.bee.shop.config.AppProperties;
import com.bee.shop.dto.*;
import com.bee.shop.entity.*;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import com.bee.shop.integration.payment.PaymentGateway;
import com.bee.shop.repository.OrderRepository;
import com.bee.shop.repository.PaymentRepository;
import com.bee.shop.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final ShippingService shipping;
    private final PaymentGateway gateway;
    private final OrderLifecycle lifecycle;
    private final OrderNumberGenerator numbers;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public OrderService(ProductRepository products, OrderRepository orders, PaymentRepository payments,
                        ShippingService shipping, PaymentGateway gateway, OrderLifecycle lifecycle,
                        OrderNumberGenerator numbers, AppProperties props, PlatformTransactionManager txManager) {
        this.products = products;
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
        this.gateway = gateway;
        this.lifecycle = lifecycle;
        this.numbers = numbers;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Checkout dua tahap: (1) transaksi DB membuat order & mengunci stok, (2) panggil payment gateway
     * di luar transaksi agar lock tidak tertahan saat menunggu HTTP. Bila gateway gagal, order dibatalkan
     * dan stok dikembalikan.
     */
    public CheckoutResponse checkout(CheckoutRequest request) {
        Long orderId = tx.execute(status -> createOrder(request));
        Order order = tx.execute(status -> orders.findById(orderId).map(this::initialized).orElseThrow());

        PaymentGateway.PaymentInit init;
        try {
            init = gateway.createPayment(order, props.order().paymentTimeoutMinutes());
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> orders.findById(orderId).ifPresent(o ->
                    lifecycle.transition(o, OrderStatus.CANCELLED, "Gagal membuat sesi pembayaran")));
            throw e;
        }

        Payment payment = tx.execute(status -> payments.save(Payment.builder()
                .order(orders.getReferenceById(orderId))
                .provider(gateway.name())
                .amount(order.getTotal())
                .status(PaymentStatus.PENDING)
                .redirectUrl(init.redirectUrl())
                .snapToken(init.snapToken())
                .build()));

        return new CheckoutResponse(order.getOrderNumber(), order.getStatus().name(), order.getTotal(),
                payment.getProvider(), payment.getRedirectUrl(), payment.getSnapToken(), order.getExpiresAt());
    }

    private Long createOrder(CheckoutRequest request) {
        // Gabungkan baris dengan produk yang sama
        Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
        for (CartItem item : request.items()) qtyByProduct.merge(item.productId(), item.quantity(), Integer::sum);

        Map<Long, Product> locked = products.lockAllByIds(qtyByProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        OffsetDateTime now = OffsetDateTime.now();
        Order order = Order.builder()
                .orderNumber(numbers.next())
                .customerName(request.customerName().trim())
                .customerEmail(request.customerEmail().trim().toLowerCase())
                .customerPhone(request.customerPhone().trim())
                .address(request.address().trim())
                .city(request.city().trim())
                .postalCode(request.postalCode().trim())
                .notes(request.notes() == null ? null : request.notes().trim())
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(now)
                .expiresAt(now.plusMinutes(props.order().paymentTimeoutMinutes()))
                .subtotal(BigDecimal.ZERO).shippingFee(BigDecimal.ZERO).total(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        int weight = 0;
        for (var entry : qtyByProduct.entrySet()) {
            Product p = locked.get(entry.getKey());
            int qty = entry.getValue();
            if (p == null || !p.isActive()) throw new BusinessException("Produk tidak tersedia: " + entry.getKey());
            if (p.getStock() < qty) {
                throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT,
                        "Stok tidak cukup untuk " + p.getName() + " (tersisa " + p.getStock() + ")");
            }
            BigDecimal line = p.getPrice().multiply(BigDecimal.valueOf(qty));
            order.addItem(OrderItem.builder()
                    .product(p).productName(p.getName()).sku(p.getSku())
                    .quantity(qty).unitPrice(p.getPrice()).lineTotal(line)
                    .build());
            subtotal = subtotal.add(line);
            weight += p.getWeightGram() * qty;
            p.setStock(p.getStock() - qty);
        }

        ShippingOption option = shipping.resolve(request.shippingCode(), order.getCity(), order.getPostalCode(), weight);
        order.setCourier(option.courier());
        order.setCourierService(option.service());
        order.setShippingEtd(option.etd());
        order.setShippingFee(option.fee());
        order.setTotalWeightGram(weight);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal.add(option.fee()));
        order.addHistory(OrderStatus.PENDING_PAYMENT, "Order dibuat, menunggu pembayaran");
        return orders.save(order).getId();
    }

    @Transactional(readOnly = true)
    public OrderResponse track(String orderNumber, String email) {
        Order order = findForCustomer(orderNumber, email);
        return OrderResponse.from(order, payments.findByOrder_Id(order.getId()).orElse(null));
    }

    @Transactional
    public OrderResponse cancelByCustomer(String orderNumber, String email) {
        Order order = findForCustomer(orderNumber, email);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("Order yang sudah dibayar tidak bisa dibatalkan sendiri. Hubungi admin.");
        }
        lifecycle.transition(order, OrderStatus.CANCELLED, "Dibatalkan oleh pelanggan");
        Payment payment = payments.findByOrder_Id(order.getId()).orElse(null);
        if (payment != null) payment.setStatus(PaymentStatus.FAILED);
        return OrderResponse.from(order, payment);
    }

    /** Membatalkan order yang melewati batas waktu pembayaran dan mengembalikan stoknya. */
    public int expireOverdueOrders() {
        List<Long> ids = orders.findExpiredIds(OrderStatus.PENDING_PAYMENT, OffsetDateTime.now());
        int expired = 0;
        for (Long id : ids) {
            try {
                Boolean done = tx.execute(status -> orders.findById(id)
                        .filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT)
                        .map(o -> {
                            lifecycle.transition(o, OrderStatus.CANCELLED, "Batas waktu pembayaran habis");
                            payments.findByOrder_Id(o.getId()).ifPresent(p -> p.setStatus(PaymentStatus.EXPIRED));
                            return true;
                        }).orElse(false));
                if (Boolean.TRUE.equals(done)) expired++;
            } catch (RuntimeException e) {
                log.warn("Gagal meng-expire order id={}", id, e);
            }
        }
        return expired;
    }

    private Order findForCustomer(String orderNumber, String email) {
        // Pesan sama untuk "tidak ada" dan "email salah" agar data order tidak bisa ditebak
        return orders.findByOrderNumber(orderNumber)
                .filter(o -> email != null && o.getCustomerEmail().equalsIgnoreCase(email.trim()))
                .orElseThrow(() -> new NotFoundException("Order tidak ditemukan"));
    }

    private Order initialized(Order o) {
        o.getItems().forEach(i -> i.getProduct().getName());
        o.getHistory().size();
        return o;
    }
}
