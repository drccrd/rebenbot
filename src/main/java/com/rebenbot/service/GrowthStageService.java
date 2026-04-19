package com.rebenbot.service;

import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.WeatherDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating vine growth stages using Growing Degree Days (GDD)
 * and mapping to BBCH (Biologische Bundesanstalt, Chemische Industrie, Biologische Testmittel) codes
 */
@Service
@Slf4j
public class GrowthStageService {

    private final WeatherDataRepository weatherDataRepository;

    public GrowthStageService(WeatherDataRepository weatherDataRepository) {
        this.weatherDataRepository = weatherDataRepository;
    }

    // BBCH Growth Stages for Grapevines
    public static final Map<String, String> BBCH_STAGES = Map.ofEntries(
        Map.entry("BUD_SWELL", "BBCH 01 - Buds begin to swell"),
        Map.entry("BUD_BREAK", "BBCH 03 - Buds break, first leaves separate"),
        Map.entry("SHOOT_GROWTH", "BBCH 05 - Shoots grow, leaves unfold"),
        Map.entry("LEAF_DEVELOPMENT", "BBCH 09 - Leaves fully developed"),
        Map.entry("INFLORESCENCE_EMERGENCE", "BBCH 10 - Inflorescence emerges"),
        Map.entry("FLOWERING", "BBCH 65 - Flowering"),
        Map.entry("FRUIT_SET", "BBCH 71 - Fruit set"),
        Map.entry("BERRY_GROWTH", "BBCH 77 - Berry growth, berries still firm"),
        Map.entry("VERAISON", "BBCH 81 - Veraison - berries begin to color"),
        Map.entry("BERRY_RIPE", "BBCH 89 - Berries ripe for harvest"),
        Map.entry("DORMANT", "BBCH 95 - Dormant, leaves fall or fallen")
    );

    // GDD Thresholds for each stage (cumulative from spring)
    // Must be a sorted map to ensure deterministic ordering
    private static final List<Map.Entry<String, Integer>> GDD_THRESHOLDS = List.of(
        Map.entry("BUD_SWELL", 0),          // Start of season
        Map.entry("BUD_BREAK", 50),         // ~50 GDD
        Map.entry("SHOOT_GROWTH", 100),     // ~100 GDD
        Map.entry("LEAF_DEVELOPMENT", 200), // ~200 GDD
        Map.entry("INFLORESCENCE_EMERGENCE", 300), // ~300 GDD
        Map.entry("FLOWERING", 400),        // ~400 GDD (typically mid-May/early June)
        Map.entry("FRUIT_SET", 500),        // ~500 GDD
        Map.entry("BERRY_GROWTH", 700),     // ~700 GDD
        Map.entry("VERAISON", 1100),        // ~1100 GDD (typically late Aug/early Sept)
        Map.entry("BERRY_RIPE", 1500),      // ~1500 GDD
        Map.entry("DORMANT", 2500)          // End of season
    );

    /**
     * Calculate Growing Degree Days from spring (April 1) to now
     * GDD = sum of (daily avg temp - base temp), where base temp = 10°C for grapevines
     */
    public double calculateAccumulatedGdd() {
        return calculateAccumulatedGdd(LocalDate.now());
    }

    /**
     * Calculate accumulated GDD up to a specific date
     */
    public double calculateAccumulatedGdd(LocalDate toDate) {
        LocalDateTime springStart = LocalDateTime.of(toDate.getYear(), 4, 1, 0, 0);
        LocalDateTime endOfDay = LocalDateTime.of(toDate, java.time.LocalTime.of(23, 59, 59));
        List<String> dateStrings = weatherDataRepository.findDistinctWeatherDates(springStart, endOfDay);

        if (dateStrings.isEmpty()) {
            log.warn("No weather data available for GDD calculation");
            return 0.0;
        }

        double totalGdd = 0.0;
        final double BASE_TEMP = 10.0;

        for (String dateStr : dateStrings) {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            LocalDateTime dateTime = date.atStartOfDay();
            Optional<Double> avgTemp = weatherDataRepository.findAverageTempForDate(dateTime);
            
            double dailyAvgTemp = avgTemp.orElse(BASE_TEMP);
            double dailyGdd = Math.max(0, dailyAvgTemp - BASE_TEMP);
            totalGdd += dailyGdd;
            
            log.debug("GDD for {}: avg_temp={:.1f}°C, daily_gdd={:.1f}°, total={:.1f}°", 
                    date, dailyAvgTemp, dailyGdd, totalGdd);
        }

        log.info("Accumulated GDD from April 1 to {}: {:.1f}", toDate, totalGdd);
        return totalGdd;
    }

    /**
     * Determine growth stage based on accumulated GDD
     */
    public String determineGrowthStageFromGdd(double gdd) {
        String stage = "BUD_SWELL";  // Default

        // Find appropriate stage based on GDD (iterate in order, highest matching stage wins)
        for (Map.Entry<String, Integer> entry : GDD_THRESHOLDS) {
            if (gdd >= entry.getValue()) {
                stage = entry.getKey();
            } else {
                break;
            }
        }

        return stage;
    }

    /**
     * Get current growth stage - returns manual override if set, otherwise GDD-calculated
     */
    public GrowthStageInfo getCurrentGrowthStage(String manualGrowthStage, Boolean isManual) {
        String stage;
        boolean isManualOverride;

        if (isManual != null && isManual && manualGrowthStage != null) {
            // Use manual override
            stage = manualGrowthStage;
            isManualOverride = true;
        } else {
            // Calculate from GDD
            double gdd = calculateAccumulatedGdd();
            stage = determineGrowthStageFromGdd(gdd);
            isManualOverride = false;
        }

        String description = BBCH_STAGES.getOrDefault(stage, "Unknown stage");
        double gdd = calculateAccumulatedGdd();
        Integer nextThreshold = GDD_THRESHOLDS.stream()
                .filter(entry -> entry.getKey().equals(stage))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        
        return new GrowthStageInfo(stage, description, gdd, nextThreshold, isManualOverride);
    }

    /**
     * DTO for growth stage information
     */
    public static class GrowthStageInfo {
        public String stageCode;           // e.g., "FLOWERING"
        public String stageBbchName;       // e.g., "BBCH 65 - Flowering"
        public double currentGdd;          // Current accumulated GDD
        public Integer gddThresholdForStage; // GDD needed to reach this stage
        public boolean isManualOverride;   // Whether this is manually set or calculated

        public GrowthStageInfo(String stageCode, String stageBbchName, double currentGdd, 
                               Integer gddThresholdForStage, boolean isManualOverride) {
            this.stageCode = stageCode;
            this.stageBbchName = stageBbchName;
            this.currentGdd = currentGdd;
            this.gddThresholdForStage = gddThresholdForStage;
            this.isManualOverride = isManualOverride;
        }
    }
}
