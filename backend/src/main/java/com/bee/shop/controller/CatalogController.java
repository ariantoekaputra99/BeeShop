package com.bee.shop.controller;

import com.bee.shop.dto.PageResponse;
import com.bee.shop.dto.ProductResponse;
import com.bee.shop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    /** sort: name (default) | price_asc | price_desc | newest */
    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "24") int size) {
        return catalog.search(q, category, sort, page, size, true);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return catalog.get(id);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return catalog.categories();
    }
}
