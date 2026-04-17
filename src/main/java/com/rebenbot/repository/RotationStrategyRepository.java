package com.rebenbot.repository;

import com.rebenbot.model.RotationStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RotationStrategyRepository extends JpaRepository<RotationStrategy, Long> {
    
    Optional<RotationStrategy> findByDiseaseId(Long diseaseId);
}
