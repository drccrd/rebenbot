package com.rebenbot.repository;

import com.rebenbot.model.InfectionRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InfectionRiskRepository extends JpaRepository<InfectionRisk, Long> {
    
    /**
     * Find latest risk assessment for a specific disease (optimized for single row).
     */
    @Query("SELECT r FROM InfectionRisk r WHERE r.disease.id = :diseaseId ORDER BY r.assessedAt DESC LIMIT 1")
    Optional<InfectionRisk> findLatestRiskByDisease(@Param("diseaseId") Long diseaseId);
    
    /**
     * Find risk assessments after a specific time (for forecasting).
     */
    @Query("SELECT r FROM InfectionRisk r WHERE r.assessedAt > :startTime ORDER BY r.assessedAt ASC")
    List<InfectionRisk> findByAssessedAtAfter(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Find recent risk assessments within time window (for history).
     */
    @Query("SELECT r FROM InfectionRisk r WHERE r.assessedAt > :startTime ORDER BY r.assessedAt DESC")
    List<InfectionRisk> findRecentRisks(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Find latest assessments grouped by disease (for dashboard).
     */
    @Query(value = "SELECT DISTINCT ON (disease_id) * FROM risk_assessment ORDER BY disease_id, assessed_at DESC", 
           nativeQuery = true)
    List<InfectionRisk> findLatestRisksByAllDiseases();
    
    /**
     * Find risks for a specific disease by ID.
     */
    @Query("SELECT r FROM InfectionRisk r WHERE r.disease.id = :diseaseId ORDER BY r.assessedAt DESC")
    List<InfectionRisk> findByDiseaseId(@Param("diseaseId") Long diseaseId);
    
    /**
     * Find risks for a disease after a specific time.
     */
    @Query("SELECT r FROM InfectionRisk r WHERE r.disease.id = :diseaseId AND r.assessedAt > :startTime ORDER BY r.assessedAt ASC")
    List<InfectionRisk> findByDiseaseIdAndAssessedAtAfter(@Param("diseaseId") Long diseaseId, @Param("startTime") LocalDateTime startTime);
}

