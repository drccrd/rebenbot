package com.rebenbot.repository;

import com.rebenbot.model.FungicideProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FungicideProductRepository extends JpaRepository<FungicideProduct, Long> {
    Optional<FungicideProduct> findByName(String name);

    Optional<FungicideProduct> findByNameIgnoreCase(String name);

    @Query("SELECT f FROM FungicideProduct f WHERE f.fracCode.id = (SELECT fc.id FROM FracCode fc WHERE fc.code = :code)")
    List<FungicideProduct> findByFracCode(@Param("code") String code);

    @Query("SELECT f FROM FungicideProduct f WHERE f.bvlApprovedInGermany = true AND f.bvlApprovalExpiry IS NOT NULL AND f.bvlApprovalExpiry <= :cutoffDate ORDER BY f.bvlApprovalExpiry ASC")
    List<FungicideProduct> findProductsWithExpiringBvlApproval(@Param("cutoffDate") LocalDate cutoffDate);
}
