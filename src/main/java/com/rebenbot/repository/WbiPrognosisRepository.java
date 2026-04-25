package com.rebenbot.repository;

import com.rebenbot.model.WbiPrognosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WbiPrognosisRepository extends JpaRepository<WbiPrognosis, Long> {

    /**
     * Get latest prognosis for a specific disease.
     */
    Optional<WbiPrognosis> findTopByDiseaseOrderByForecastDateDesc(String disease);

    /**
     * Get all prognoses for a disease within a date range.
     */
    List<WbiPrognosis> findByDiseaseAndForecastDateBetweenOrderByForecastDateDesc(
            String disease, LocalDate startDate, LocalDate endDate);

    /**
     * Get prognosis for specific date and disease.
     */
    Optional<WbiPrognosis> findByDiseaseAndForecastDate(String disease, LocalDate forecastDate);
}
