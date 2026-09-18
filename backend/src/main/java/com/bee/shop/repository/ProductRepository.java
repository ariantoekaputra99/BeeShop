package com.bee.shop.repository;

import com.bee.shop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProductRepository extends JpaRepository<Product,Long>{List<Product> findByActiveTrueOrderByNameAsc();}
