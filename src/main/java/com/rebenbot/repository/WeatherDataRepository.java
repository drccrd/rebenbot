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

    boolean existsByVineyardIdAndRecordedAt(Long vineyardId, LocalDateTime recordedAt);
    
    /**
     * Find current weather data: the most recent record at or before NOW.
     */
    @Query("SELECT w FROM WeatherData w WHERE w.recordedAt <= :now ORDER BY w.recordedAt DESC LIMIT 1")
    Optional<WeatherData> findCurrentWeatherData(@Param("now") LocalDateTime now);
    
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
    
    /**
     * Calculate total precipitation in the last 24 hours (database-level aggregation).
     * Returns 0.0 if no data found.
     */
    @Query("SELECT COALESCE(SUM(w.precipitationMm), 0.0) FROM WeatherData w WHERE w.recordedAt > :startTime")
    Double sumPrecipitationSince(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Find the most recent weather record with significant precipitation.
     */
    @Query("SELECT w FROM WeatherData w WHERE w.recordedAt > :startTime AND w.precipitationMm >= :minPrecipitation ORDER BY w.recordedAt DESC LIMIT 1")
    Optional<WeatherData> findMostRecentSignificantRain(@Param("startTime") LocalDateTime startTime, @Param("minPrecipitation") Double minPrecipitation);
    
    /**
     * Find next significant rain event (forecast).
     */
    @Query("SELECT w FROM WeatherData w WHERE w.recordedAt > :now AND w.recordedAt <= :endTime AND w.precipitationMm >= :minPrecipitation ORDER BY w.recordedAt ASC LIMIT 1")
    Optional<WeatherData> findNextSignificantRain(@Param("now") LocalDateTime now, @Param("endTime") LocalDateTime endTime, @Param("minPrecipitation") Double minPrecipitation);
    
    /**
     * Calculate average temperature for a specific date.
     * Used for GDD calculations without fetching all records.
     */
    @Query("SELECT AVG(w.temperatureC) FROM WeatherData w WHERE DATE(w.recordedAt) = :date")
    Optional<Double> findAverageTempForDate(@Param("date") LocalDateTime date);
    
    /**
     * Get all distinct dates with weather data in a time range.
     */
    @Query(value = "SELECT DISTINCT CAST(w.recorded_at AS DATE) FROM weather_data w WHERE w.recorded_at >= :startTime AND w.recorded_at < :endTime ORDER BY CAST(w.recorded_at AS DATE)", nativeQuery = true)
    List<String> findDistinctWeatherDates(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * Recent records for a specific vineyard — used for mean temperature calculation.
     * Limit 14 covers approximately 7 days at twice-daily fetch cadence.
     */
    List<WeatherData> findTop14ByVineyardIdOrderByRecordedAtDesc(Long vineyardId);
}

