package com.rebenbot.service;

import com.rebenbot.repository.WeatherDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
        Map.entry("FIRST_LEAVES", "BBCH 09 - First signs of bud break"),
        Map.entry("ONE_LEAF", "BBCH 11 - First leaf unfolded"),
        Map.entry("TWO_LEAVES", "BBCH 12 - Two leaves unfolded"),
        Map.entry("THREE_LEAVES", "BBCH 13 - Three leaves unfolded"),
        Map.entry("FOUR_LEAVES", "BBCH 14 - Four leaves unfolded"),
        Map.entry("FIVE_LEAVES", "BBCH 15 - Five leaves unfolded"),
        Map.entry("SIX_LEAVES", "BBCH 16 - Six leaves unfolded"),
        Map.entry("SEVEN_LEAVES", "BBCH 17 - Seven leaves unfolded"),
        Map.entry("EIGHT_LEAVES", "BBCH 18 - Eight or more leaves unfolded"),
        Map.entry("INFLORESCENCE_EMERGENCE", "BBCH 50-55 - Inflorescence visible"),
        Map.entry("FLOWERING", "BBCH 60-68 - Flowering"),
        Map.entry("FRUIT_SET", "BBCH 70-73 - Fruit set"),
        Map.entry("BERRY_GROWTH", "BBCH 75-79 - Berry growth"),
        Map.entry("VERAISON", "BBCH 81-88 - Veraison - berries changing color"),
        Map.entry("BERRY_RIPE", "BBCH 89 - Berries ripe for harvest"),
        Map.entry("DORMANT", "BBCH 95 - Dormant, leaves fall or fallen")
    );

    // GDD Thresholds for each stage (cumulative from spring)
    // Calibrated against vitimeteo Molitor (2014) model for Schriesheim region
    // Must be a sorted map to ensure deterministic ordering
    private static final List<Map.Entry<String, Integer>> GDD_THRESHOLDS = List.of(
        Map.entry("BUD_SWELL", 0),          // BBCH 01 - Start of season
        Map.entry("FIRST_LEAVES", 3),       // BBCH 09 - First signs of bud break (new leaves visible)
        Map.entry("ONE_LEAF", 15),          // BBCH 11 - First leaf unfolded
        Map.entry("TWO_LEAVES", 26),        // BBCH 12 - Two leaves unfolded
        Map.entry("THREE_LEAVES", 34),      // BBCH 13 - Three leaves unfolded
        Map.entry("FOUR_LEAVES", 44),       // BBCH 14 - Four leaves unfolded
        Map.entry("FIVE_LEAVES", 55),       // BBCH 15 - Five leaves unfolded
        Map.entry("SIX_LEAVES", 66),        // BBCH 16 - Six leaves unfolded
        Map.entry("SEVEN_LEAVES", 77),      // BBCH 17 - Seven leaves unfolded
        Map.entry("EIGHT_LEAVES", 88),      // BBCH 18 - Eight or more leaves unfolded
        Map.entry("INFLORESCENCE_EMERGENCE", 110), // BBCH 50+ - Flower cluster development
        Map.entry("FLOWERING", 130),        // BBCH 60+ - Flowering
        Map.entry("FRUIT_SET", 160),        // BBCH 70+ - Fruit set
        Map.entry("BERRY_GROWTH", 200),     // BBCH 75+ - Berries growing
        Map.entry("VERAISON", 250),         // BBCH 81+ - Véraison (berries changing color)
        Map.entry("BERRY_RIPE", 300),       // BBCH 89 - Berries fully ripe
        Map.entry("DORMANT", 400)           // End of season
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
            log.warn("No weather data available for GDD calculation between {} and {}", springStart, endOfDay);
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
        }

        log.info("Accumulated GDD from April 1 to {}: {}°", toDate, String.format("%.1f", totalGdd));
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
