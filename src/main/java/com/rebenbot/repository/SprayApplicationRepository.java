package com.rebenbot.repository;

import com.rebenbot.model.SprayApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SprayApplicationRepository extends JpaRepository<SprayApplication, Long> {
    List<SprayApplication> findByVineyardIdOrderByApplicationDateDesc(Long vineyardId);
    List<SprayApplication> findByVineyardIdAndApplicationDateBetweenOrderByApplicationDateDesc(
            Long vineyardId, LocalDateTime start, LocalDateTime end);
    List<SprayApplication> findByVineyardIdAndFungicideProductIdOrderByApplicationDateDesc(
            Long vineyardId, Long fungicideProductId);
}
