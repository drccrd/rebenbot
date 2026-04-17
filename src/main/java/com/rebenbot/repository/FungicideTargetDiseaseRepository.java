package com.rebenbot.repository;

import com.rebenbot.model.FungicideTargetDisease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FungicideTargetDiseaseRepository extends JpaRepository<FungicideTargetDisease, Long> {
    
    List<FungicideTargetDisease> findByDiseaseId(Long diseaseId);
    
    List<FungicideTargetDisease> findByProductId(Long productId);
}
