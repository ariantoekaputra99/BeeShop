package com.bee.shop.service;

import com.bee.shop.dto.*;
import com.bee.shop.entity.*;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import com.bee.shop.repository.OrderRepository;
import com.bee.shop.repository.PaymentRepository;
import com.bee.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    public static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderLifecycle lifecycle;

    // ---------- Produk ----------

    public ProductResponse createProduct(ProductRequest r) {
        if (products.existsBySku(r.sku())) throw new BusinessException(HttpStatus.CONFLICT, "SKU sudah dipakai");
        Product p = new Product();
        apply(p, r);
        return ProductResponse.from(products.save(p));
    }

    public ProductResponse updateProduct(Long id, ProductRequest r) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Produk tidak ditemukan"));
        if (!p.getSku().equals(r.sku()) && products.existsBySku(r.sku())) {
            throw new BusinessException(HttpStatus.CONFLICT, "SKU sudah dipakai");
        }
        apply(p, r);
        return ProductResponse.from(p);
    }

    /** Soft delete: produk dinonaktifkan agar riwayat order tetap utuh. */
    public void deactivateProduct(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Produk tidak ditemukan"));
        p.setActive(false);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return products.findById(id).map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Produk tidak ditemukan"));
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim());
        p.setSku(r.sku().trim());
        p.setPrice(r.price());
        p.setStock(r.stock());
        p.setWeightGram(r.weightGram());
        p.setCategory(blankToNull(r.category()));
        p.setImageUrl(blankToNull(r.imageUrl()));
        p.setDescription(blankToNull(r.description()));
        p.setActive(r.active() == null || r.active());
    }

    // ---------- Order ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> searchOrders(OrderStatus status, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return PageResponse.of(orders.search(status, q == null ? "" : q.trim(), pageable),
                o -> OrderResponse.from(o, payments.findByOrder_Id(o.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNumber) {
        Order o = findOrder(orderNumber);
        return OrderResponse.from(o, payments.findByOrder_Id(o.getId()).orElse(null));
    }

    public OrderResponse updateStatus(String orderNumber, OrderStatusUpdateRequest r) {
        Order o = findOrder(orderNumber);
        if (r.status() == OrderStatus.PAID) {
            throw new BusinessException("Status PAID hanya boleh di-set oleh payment gateway");
        }
        if (r.status() == OrderStatus.SHIPPED) {
            if (r.trackingNumber() == null || r.trackingNumber().isBlank()) {
                throw new BusinessException("Nomor resi wajib diisi untuk status SHIPPED");
            }
            o.setTrackingNumber(r.trackingNumber().trim());
        }
        String note = r.note() != null && !r.note().isBlank() ? r.note().trim()
                : r.status() == OrderStatus.SHIPPED ? "Dikirim, resi " + o.getTrackingNumber() : "Diperbarui admin";
        lifecycle.transition(o, r.status(), note);
        return OrderResponse.from(o, payments.findByOrder_Id(o.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(OrderStatus.values()).forEach(s -> byStatus.put(s.name(), orders.countByStatus(s)));
        long active = products.search("", "", true, PageRequest.of(0, 1)).getTotalElements();
        long lowStock = products.findAll().stream()
                .filter(p -> p.isActive() && p.getStock() <= LOW_STOCK_THRESHOLD).count();
        return new DashboardStats(orders.sumRevenue(), byStatus, active, lowStock);
    }

    private Order findOrder(String orderNumber) {
        return orders.findByOrderNumber(orderNumber).orElseThrow(() -> new NotFoundException("Order tidak ditemukan"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
