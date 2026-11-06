package com.stockpulse.service;

import com.stockpulse.entity.Category;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") String id);

  boolean existsBySku(String sku);

  @Query(
      "select p from Product p where (:status is null or p.status = :status)"
          + " and (:category is null or p.category = :category) order by p.sku")
  List<Product> search(@Param("status") ProductStatus status, @Param("category") Category category);

  @Query(
      "select coalesce(avg(p.demandVelocity), 0), count(p) from Product p"
          + " where p.category = :category and p.id <> :excludeId")
  List<Object[]> peerVelocity(
      @Param("category") Category category, @Param("excludeId") String excludeId);

  @Query(value = "select nextval('product_id_seq')", nativeQuery = true)
  long nextIdValue();
}
