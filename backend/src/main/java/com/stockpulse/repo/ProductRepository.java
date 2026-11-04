package com.stockpulse.repo;

import com.stockpulse.domain.Product;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {
    boolean existsBySkuIgnoreCase(String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(@Param("id") String id);

    @Query("select avg(p.demandVelocity) from Product p where p.category = :category and p.id <> :id")
    BigDecimal averagePeerDemand(@Param("category") String category, @Param("id") String id);
}
