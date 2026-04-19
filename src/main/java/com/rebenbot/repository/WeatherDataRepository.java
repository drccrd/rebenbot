package com.rebenbot.repository;

import com.rebenbot.model.WeatherData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherDataRepository extends JpaRepository<WeatherData, Long> {
    
    /**
     * Find the most recent weather record (optimized for single row).
     * Replaces: findAll().stream().max(Comparator.comparing(WeatherData::getRecordedAt))
     */
    Optional<WeatherData> findTopByOrderByRecordedAtDesc();
    
    /**
     * Find weather records after a specific date (optimized with WHERE clause).
     * Replaces: findAll().stream().filter(w -> w.getRecordedAt().isAfter(cutoff))
     */
    @Query("SELECT w FROM WeatherData w WHERE w.recordedAt > :startTime ORDER BY w.recordedAt ASC")
    List<WeatherData> findByRecordedAtAfter(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Find recent weather records within last N days (for rainfall analysis).
     */
    @Query("SELECT w FROM WeatherData w WHERE w.recordedAt > :startTime ORDER BY w.recordedAt DESC")
    List<WeatherData> findRecentData(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Find weather records for a specific vineyard ordered by time.
     */
    @Query("SELECT w FROM WeatherData w WHERE w.vineyard.id = :vineyardId ORDER BY w.recordedAt DESC")
    List<WeatherData> findByVineyardIdOrderByRecordedAtDesc(@Param("vineyardId") Long vineyardId);
}
