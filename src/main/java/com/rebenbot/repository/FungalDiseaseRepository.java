package com.rebenbot.repository;

import com.rebenbot.model.FungalDisease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FungalDiseaseRepository extends JpaRepository<FungalDisease, Long> {
    FungalDisease findByCommonName(String commonName);
    Optional<FungalDisease> findByEppoCode(String eppoCode);
}
