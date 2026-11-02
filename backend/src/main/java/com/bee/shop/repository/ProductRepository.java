package com.bee.shop.repository;

import com.bee.shop.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where (:activeOnly = false or p.active = true)
              and (:category = '' or p.category = :category)
              and (:q = ''
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.sku) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("q") String q,
                         @Param("category") String category,
                         @Param("activeOnly") boolean activeOnly,
                         Pageable pageable);

    @Query("select distinct p.category from Product p where p.active = true and p.category is not null order by p.category")
    List<String> findActiveCategories();

    Optional<Product> findByIdAndActiveTrue(Long id);

    Optional<Product> findBySku(String sku);

    List<Product> findBySkuIn(Collection<String> skus);

    boolean existsBySku(String sku);

    /** Dikunci saat checkout supaya dua pembeli tidak mengambil stok terakhir bersamaan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllByIds(@Param("ids") Collection<Long> ids);
}
