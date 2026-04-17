package com.rebenbot.repository;

import com.rebenbot.model.FungicideApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FungicideApprovalRepository extends JpaRepository<FungicideApproval, Long> {
    
    List<FungicideApproval> findByRegion(String region);
    
    List<FungicideApproval> findByProductId(Long productId);
    
    Optional<FungicideApproval> findByProductIdAndRegion(Long productId, String region);
    
    @Query("SELECT fa FROM FungicideApproval fa WHERE fa.region = :region AND fa.approvalStatus = 'ACTIVE'")
    List<FungicideApproval> findActiveApprovalsByRegion(@Param("region") String region);
}
