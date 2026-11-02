package com.bee.shop.service;

import com.bee.shop.dto.PageResponse;
import com.bee.shop.dto.ProductResponse;
import com.bee.shop.exception.NotFoundException;
import com.bee.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;

    public PageResponse<ProductResponse> search(String q, String category, String sort, int page, int size, boolean activeOnly) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), toSort(sort));
        return PageResponse.of(products.search(blankToEmpty(q), blankToEmpty(category), activeOnly, pageable),
                ProductResponse::from);
    }

    public ProductResponse get(Long id) {
        return products.findByIdAndActiveTrue(id).map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Produk tidak ditemukan"));
    }

    public List<String> categories() {
        return products.findActiveCategories();
    }

    static Sort toSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> Sort.by("name").ascending();
        };
    }

    static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
