package com.rebenbot.service;

import com.rebenbot.repository.WeatherDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Set;

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
        Map.entry("INFLORESCENCE_EMERGENCE", "BBCH 53 - Inflorescence separated / visible"),
        Map.entry("FLOWERING", "BBCH 65 - Full flowering"),
        Map.entry("FRUIT_SET", "BBCH 71 - Fruit set"),
        Map.entry("BERRY_GROWTH", "BBCH 77 - Berry development (bunch closure)"),
        Map.entry("VERAISON", "BBCH 85 - Véraison — berries changing colour"),
        Map.entry("BERRY_RIPE", "BBCH 89 - Berries ripe for harvest"),
        Map.entry("DORMANT", "BBCH 95 - Dormant, leaves fall or fallen")
    );

    /**
     * Representative BBCH numeric code for each stage key.
     * For ranges (e.g. flowering BBCH 60-68) a representative midpoint is used.
     * Source: Lorenz et al. (1994) "Growth Stages of the Grapevine",
     *         Vitis 33, 249-255.
     */
    public static final Map<String, Integer> STAGE_TO_BBCH = Map.ofEntries(
        Map.entry("BUD_SWELL",               1),
        Map.entry("FIRST_LEAVES",            9),
        Map.entry("ONE_LEAF",               11),
        Map.entry("TWO_LEAVES",             12),
        Map.entry("THREE_LEAVES",           13),
        Map.entry("FOUR_LEAVES",            14),
        Map.entry("FIVE_LEAVES",            15),
        Map.entry("SIX_LEAVES",             16),
        Map.entry("SEVEN_LEAVES",           17),
        Map.entry("EIGHT_LEAVES",           18),
        Map.entry("INFLORESCENCE_EMERGENCE",53),
        Map.entry("FLOWERING",              65),
        Map.entry("FRUIT_SET",              71),
        Map.entry("BERRY_GROWTH",           77),
        Map.entry("VERAISON",               85),
        Map.entry("BERRY_RIPE",             89),
        Map.entry("DORMANT",                95)
    );

    // Stages that describe shoot/leaf development (BBCH 01-18)
    private static final Set<String> SHOOT_STAGES = Set.of(
        "BUD_SWELL", "FIRST_LEAVES", "ONE_LEAF", "TWO_LEAVES", "THREE_LEAVES",
        "FOUR_LEAVES", "FIVE_LEAVES", "SIX_LEAVES", "SEVEN_LEAVES", "EIGHT_LEAVES"
    );

    // Stages that describe berry/inflorescence development (BBCH 53+)
    private static final Set<String> BERRY_STAGES = Set.of(
        "INFLORESCENCE_EMERGENCE", "FLOWERING", "FRUIT_SET", "BERRY_GROWTH",
        "VERAISON", "BERRY_RIPE", "DORMANT"
    );

    // GDD Thresholds for each stage (base 10 °C, cumulative from April 1)
    // Source: Lorenz et al. (1994), DWD phenology data for SW-Germany, JKI Geilweilerhof.
    private static final List<Map.Entry<String, Integer>> GDD_THRESHOLDS = List.of(
        Map.entry("BUD_SWELL", 0),               // BBCH 01 - Buds begin to swell
        Map.entry("FIRST_LEAVES", 5),             // BBCH 09 - First green leaf tips visible
        Map.entry("ONE_LEAF", 20),                // BBCH 11 - 1 leaf unfolded (~15–25 GDD)
        Map.entry("TWO_LEAVES", 35),              // BBCH 12 - 2 leaves unfolded
        Map.entry("THREE_LEAVES", 50),            // BBCH 13
        Map.entry("FOUR_LEAVES", 65),             // BBCH 14
        Map.entry("FIVE_LEAVES", 80),             // BBCH 15
        Map.entry("SIX_LEAVES", 95),              // BBCH 16
        Map.entry("SEVEN_LEAVES", 112),           // BBCH 17
        Map.entry("EIGHT_LEAVES", 128),           // BBCH 18 - 8+ leaves unfolded
        Map.entry("INFLORESCENCE_EMERGENCE", 190),// BBCH 53 - Inflorescence separated
        Map.entry("FLOWERING", 290),              // BBCH 65 - Full flowering
        Map.entry("FRUIT_SET", 400),              // BBCH 71 - Fruit set
        Map.entry("BERRY_GROWTH", 530),           // BBCH 77 - Bunch closure
        Map.entry("VERAISON", 730),               // BBCH 85 - Véraison
        Map.entry("BERRY_RIPE", 940),             // BBCH 89 - Harvest ripe
        Map.entry("DORMANT", 1150)                // Leaf fall / dormant
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
        List<java.time.LocalDate> dates = weatherDataRepository.findDistinctWeatherDates(springStart, endOfDay);

        if (dates.isEmpty()) {
            log.warn("No weather data available for GDD calculation between {} and {}", springStart, endOfDay);
            return 0.0;
        }

        double totalGdd = 0.0;
        final double BASE_TEMP = 10.0;

        for (java.time.LocalDate date : dates) {
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
     * Get current growth stage based on accumulated GDD.
     */
    public GrowthStageInfo getCurrentGrowthStage() {
        double gdd = calculateAccumulatedGdd();
        String stage = determineGrowthStageFromGdd(gdd);
        String description = BBCH_STAGES.getOrDefault(stage, "Unknown stage");
        Integer nextThreshold = GDD_THRESHOLDS.stream()
                .filter(entry -> entry.getKey().equals(stage))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        int bbch = STAGE_TO_BBCH.getOrDefault(stage, 0);

        // Shoot/leaf track: BBCH 01–18 while leaves are counting; BBCH 18+ once in berry stages
        int shootBbch    = (bbch <= 18) ? bbch : 18;
        String shootName = SHOOT_STAGES.contains(stage)
                ? description
                : "BBCH 18+ — 8 or more leaves unfolded";

        // Berry/inflorescence track: BBCH 53+ once inflorescence has emerged; not yet before
        int berryBbch    = BERRY_STAGES.contains(stage) ? bbch : 0;
        String berryName = BERRY_STAGES.contains(stage)
                ? description
                : "Not yet visible (below BBCH 51)";

        return new GrowthStageInfo(stage, description, gdd, nextThreshold,
                shootBbch, shootName, berryBbch, berryName);
    }

    /**
     * DTO for growth stage information.
     *
     * Shoot and berry stages run in parallel:
     *   - shootBbch / shootStageName: the BBCH 01-18 leaf-development track
     *   - berryBbch / berryStageName: the BBCH 53-89 inflorescence/berry track
     *     (0 / "Not yet visible" before inflorescence has emerged)
     */
    public static class GrowthStageInfo {
        public String stageCode;             // e.g., "FLOWERING"
        public String stageBbchName;         // human-readable description of the current stage
        public double currentGdd;            // accumulated GDD since April 1
        public Integer gddThresholdForStage; // GDD at which this stage begins
        public int shootBbch;                // BBCH 01-18 shoot/leaf stage code
        public String shootStageName;        // e.g., "BBCH 14 - Four leaves unfolded"
        public int berryBbch;                // BBCH 53-89 berry stage code (0 = not yet emerged)
        public String berryStageName;        // e.g., "BBCH 65 - Full flowering"

        public GrowthStageInfo(String stageCode, String stageBbchName, double currentGdd,
                               Integer gddThresholdForStage,
                               int shootBbch, String shootStageName,
                               int berryBbch, String berryStageName) {
            this.stageCode = stageCode;
            this.stageBbchName = stageBbchName;
            this.currentGdd = currentGdd;
            this.gddThresholdForStage = gddThresholdForStage;
            this.shootBbch = shootBbch;
            this.shootStageName = shootStageName;
            this.berryBbch = berryBbch;
            this.berryStageName = berryStageName;
        }
    }
}
