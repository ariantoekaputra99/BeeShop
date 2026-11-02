package com.bee.shop.repository;

import com.bee.shop.entity.Order;
import com.bee.shop.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
            select o from Order o
            where (:status is null or o.status = :status)
              and (:q = ''
                   or lower(o.orderNumber) like lower(concat('%', :q, '%'))
                   or lower(o.customerName) like lower(concat('%', :q, '%'))
                   or lower(o.customerEmail) like lower(concat('%', :q, '%')))
            """)
    Page<Order> search(@Param("status") OrderStatus status, @Param("q") String q, Pageable pageable);

    @Query("select o.id from Order o where o.status = :status and o.expiresAt < :now")
    List<Long> findExpiredIds(@Param("status") OrderStatus status, @Param("now") OffsetDateTime now);

    @Query("""
            select o from Order o
            where o.paidAt is not null and o.paidAt >= :since
            order by o.paidAt asc
            """)
    List<Order> findPaidSince(@Param("since") OffsetDateTime since);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.paidAt is not null and o.status <> com.bee.shop.entity.OrderStatus.REFUNDED")
    java.math.BigDecimal sumRevenue();
}
