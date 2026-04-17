package com.rebenbot.repository;

import com.rebenbot.model.InfectionRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfectionRiskRepository extends JpaRepository<InfectionRisk, Long> {
}
