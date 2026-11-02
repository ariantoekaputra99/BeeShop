package com.bee.shop.controller.admin;

import com.bee.shop.auth.AdminPrincipal;
import com.bee.shop.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import com.bee.shop.entity.OrderStatus;
import com.bee.shop.service.AdminService;
import com.bee.shop.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Semua endpoint di sini butuh sesi login admin (lihat AuthFilter). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService admin;
    private final CatalogService catalog;

    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return admin.dashboard();
    }

    // ---- Produk ----

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return catalog.search(q, category, sort, page, size, false);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return admin.getProduct(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return admin.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return admin.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        admin.deactivateProduct(id);
    }

    // ---- Order ----

    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@RequestParam(required = false) OrderStatus status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return admin.searchOrders(status, q, page, size);
    }

    @GetMapping("/orders/{orderNumber}")
    public OrderResponse order(@PathVariable String orderNumber) {
        return admin.getOrder(orderNumber);
    }

    @PatchMapping("/orders/{orderNumber}/status")
    public OrderResponse updateStatus(@PathVariable String orderNumber, @Valid @RequestBody OrderStatusUpdateRequest request,
                                      HttpServletRequest http) {
        return admin.updateStatus(orderNumber, request, AdminPrincipal.from(http));
    }
}
