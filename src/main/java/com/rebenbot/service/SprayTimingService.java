package com.rebenbot.service;

import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.WeatherDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating spray timing recommendations based on weather conditions.
 * Implements disease-specific spray windows, rainfall tracking, and incubation period calculations.
 */
@Service
@Slf4j
public class SprayTimingService {

    private final WeatherDataRepository weatherDataRepository;

    public SprayTimingService(WeatherDataRepository weatherDataRepository) {
        this.weatherDataRepository = weatherDataRepository;
    }

    public static final double SIGNIFICANT_RAIN_MM = 2.0;  // Rain threshold
    public static final double SPRAY_WINDOW_DRY_TIME_HOURS = 4.0;  // Hours needed dry after spray
    public static final double SPRAY_DURATION_HOURS = 2.5;  // Average spray duration (2-3 hours)

    /**
     * Calculate weather-dependent incubation period for Peronospora.
     * 
     * Base: 5 days minimum (at optimal conditions)
     * Adjusted by temperature:
     * - 10-15°C: longer (colder = slower development)
     * - 15-22°C: shorter (optimal range, near 5 days)
     * - 22-25°C: slower (warmer than optimal)
     * - <10°C or >25°C: very slow (outside range)
     * 
     * Returns incubation period in hours.
     */
    public double calculatePeronosporaIncubationPeriod(WeatherData weather) {
        double tempC = weather.getTemperatureC();
        double baseDaysIncubation = 5.0;  // Minimum at optimal temp

        double adjustmentFactor = 1.0;

        if (tempC < 5.0) {
            adjustmentFactor = 2.0;  // Very slow development
        } else if (tempC < 10.0) {
            adjustmentFactor = 1.5;  // Slower
        } else if (tempC < 15.0) {
            adjustmentFactor = 1.1;  // Slightly slower
        } else if (tempC <= 22.0) {
            adjustmentFactor = 1.0;  // Optimal range, baseline 5 days
        } else if (tempC <= 25.0) {
            adjustmentFactor = 1.1;  // Slightly slower
        } else {
            adjustmentFactor = 1.5;  // Much slower, outside optimal range
        }

        double incubationDays = baseDaysIncubation * adjustmentFactor;
        double incubationHours = incubationDays * 24.0;

        log.debug("Peronospora incubation period at {}°C: {} days ({} hours)",
                tempC, String.format("%.1f", incubationDays), String.format("%.1f", incubationHours));

        return incubationHours;
    }

    /**
     * Get recommended spray timing based on incubation period.
     * Best practice: spray at ~80% of incubation period for preventive coverage.
     * Returns hours from now when spray should occur.
     */
    public double getRecommendedSprayTiming(double incubationHours) {
        // Spray at 80% of incubation period
        double sprayTimingHours = incubationHours * 0.80;
        return sprayTimingHours;
    }

    /**
     * Calculate 24-hour cumulative rainfall from weather data.
     * Returns cumulative precipitation in mm over the last 24 hours.
     */
    public double calculate24HourRainfall() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<WeatherData> recentData = weatherDataRepository.findRecentData(cutoff);

        double totalRainfall = 0.0;
        for (WeatherData weather : recentData) {
            if (weather.getPrecipitationMm() != null && weather.getRecordedAt().isBefore(LocalDateTime.now())) {
                totalRainfall += weather.getPrecipitationMm();
            }
        }

        log.debug("24-hour cumulative rainfall: {} mm", String.format("%.2f", totalRainfall));
        return totalRainfall;
    }

    /**
     * Calculate hours since last significant rain event (>2mm).
     * Returns null if no significant rain in last 72 hours.
     */
    public Double getHoursSinceLastSignificantRain() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(72);
        List<WeatherData> recentData = weatherDataRepository.findRecentData(cutoff);

        for (WeatherData weather : recentData) {
            if (weather.getPrecipitationMm() != null && weather.getPrecipitationMm() >= SIGNIFICANT_RAIN_MM) {
                long hoursSinceLong = java.time.temporal.ChronoUnit.HOURS.between(weather.getRecordedAt(), LocalDateTime.now());
                double hoursSince = (double) hoursSinceLong;
                log.debug("Last significant rain (>{}mm) was {} hours ago", SIGNIFICANT_RAIN_MM, String.format("%.1f", hoursSince));
                return hoursSince;
            }
        }

        log.debug("No significant rain (>{}mm) detected in last 72 hours", SIGNIFICANT_RAIN_MM);
        return null;
    }

    /**
     * Determine optimal spray window for Peronospora.
     * 
     * Strategy:
     * 1. Check if significant rain is coming in forecast
     * 2. If yes: recommend spray BEFORE rain (optimally 80% of incubation period before)
     * 3. If rain just occurred: recommend spray immediately (within 2 hours)
     * 4. Calculate window considering spray duration (2-3 hours) and dry time needed (4 hours)
     */
    public SprayWindow getOptimalSprayWindow(double incubationHours) {
        Double hoursSinceRain = getHoursSinceLastSignificantRain();
        double sprayRecommendationHours = getRecommendedSprayTiming(incubationHours);

        // Look ahead 48 hours for significant rain
        LocalDateTime lookAhead = LocalDateTime.now().plusHours(48);
        List<WeatherData> forecastData = weatherDataRepository.findByRecordedAtAfter(LocalDateTime.now());
        forecastData = forecastData.stream()
                .filter(w -> w.getRecordedAt().isBefore(lookAhead))
                .sorted(Comparator.comparing(WeatherData::getRecordedAt))
                .collect(Collectors.toList());

        // Find if rain is coming
        Optional<WeatherData> nextRain = forecastData.stream()
                .filter(w -> w.getPrecipitationMm() != null && w.getPrecipitationMm() >= SIGNIFICANT_RAIN_MM)
                .findFirst();

        String strategy;
        String strategyReasoning;
        double preferredSprayHourFromNow;
        double windowStartHours;
        double windowEndHours;
        String preferredTimeReasoning;
        String windowReasoning;

        if (hoursSinceRain != null && hoursSinceRain < 4) {
            // Rain just happened - splash dispersal event
            strategy = "AFTER_RAIN";
            strategyReasoning = String.format("Rain detected %.1f hours ago. Fungicide needed to prevent spore germination on wet leaves.", hoursSinceRain);
            preferredSprayHourFromNow = 1.0;  // Spray ASAP
            preferredTimeReasoning = "Spray immediately after rain (within 1 hour) while conditions are optimal for fungicide adhesion";
            windowStartHours = 0.0;
            windowEndHours = 2.0;  // Within 2 hours of rain event
            windowReasoning = "Window closes 2 hours after rain - spore germination happens quickly on wet leaves";
        } else if (nextRain.isPresent()) {
            // Rain is coming - spray BEFORE rain
            long hoursUntilRainLong = java.time.temporal.ChronoUnit.HOURS.between(
                    LocalDateTime.now(), nextRain.get().getRecordedAt());
            double hoursUntilRain = (double) hoursUntilRainLong;
            strategy = "BEFORE_RAIN";
            strategyReasoning = String.format("Rain forecast in %.0f hours. Must spray before rainfall to maximize fungicide coverage.", hoursUntilRain);
            preferredSprayHourFromNow = Math.max(0, hoursUntilRain - SPRAY_DURATION_HOURS - SPRAY_WINDOW_DRY_TIME_HOURS);
            preferredTimeReasoning = String.format("Spray %.0f hours before rain. Requires %.1f hours to apply + %.1f hours dry time before rain arrives.", 
                    (SPRAY_DURATION_HOURS + SPRAY_WINDOW_DRY_TIME_HOURS), SPRAY_DURATION_HOURS, SPRAY_WINDOW_DRY_TIME_HOURS);
            windowStartHours = Math.max(0, hoursUntilRain - SPRAY_DURATION_HOURS - SPRAY_WINDOW_DRY_TIME_HOURS);
            windowEndHours = Math.max(preferredSprayHourFromNow, hoursUntilRain - SPRAY_DURATION_HOURS);
            windowReasoning = "Window closes when spray cannot fully dry before rain. Earlier spray is better for fungicide effectiveness.";
        } else {
            // No rain imminent - use preventive timing (80% of incubation)
            strategy = "PREVENTIVE";
            strategyReasoning = "No rain forecasted. Using preventive spray schedule to establish protection before infection occurs.";
            preferredSprayHourFromNow = sprayRecommendationHours;
            preferredTimeReasoning = String.format("Spray at 80%% of incubation period (%.1f hours) for optimal protection. Incubation period is %.1f hours based on current temperature.", 
                    sprayRecommendationHours, incubationHours);
            windowStartHours = Math.max(0, sprayRecommendationHours - 6);  // ±6 hour window
            windowEndHours = sprayRecommendationHours + 6;
            windowReasoning = "±6 hour window around optimal time allows flexibility while maintaining effective disease prevention.";
        }

        SprayWindow window = new SprayWindow(
                strategy,
                LocalDateTime.now().plusHours((long) preferredSprayHourFromNow),
                LocalDateTime.now().plusHours((long) windowStartHours),
                LocalDateTime.now().plusHours((long) windowEndHours),
                SPRAY_DURATION_HOURS,
                SPRAY_WINDOW_DRY_TIME_HOURS
        );
        
        // Add reasoning fields
        window.strategyReasoning = strategyReasoning;
        window.incubationPeriodHours = incubationHours;
        window.preferredTimeReasoning = preferredTimeReasoning;
        window.windowReasoning = windowReasoning;
        
        return window;
    }

    /**
     * Calculate days since last spray.
     * Returns null if no spray recorded.
     */
    public Double calculateDaysSinceLastSpray(LocalDateTime lastSprayDate) {
        if (lastSprayDate == null) {
            return null;
        }
        long hoursSinceLong = java.time.temporal.ChronoUnit.HOURS.between(lastSprayDate, LocalDateTime.now());
        double hoursSince = (double) hoursSinceLong;
        double daysSince = hoursSince / 24.0;
        log.debug("Days since last spray: {}", String.format("%.1f", daysSince));
        return daysSince;
    }

    /**
     * DTO for spray timing information
     */
    public static class SprayWindow {
        public String strategy;  // PREVENTIVE, BEFORE_RAIN, AFTER_RAIN
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        public LocalDateTime preferredTime;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        public LocalDateTime windowStart;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        public LocalDateTime windowEnd;
        
        public double sprayDurationHours;
        public double dryTimeRequiredHours;
        
        // Reasoning and calculation details
        public String strategyReasoning;
        public double incubationPeriodHours;
        public String preferredTimeReasoning;
        public String windowReasoning;

        public SprayWindow(String strategy, LocalDateTime preferredTime, LocalDateTime windowStart,
                           LocalDateTime windowEnd, double sprayDurationHours, double dryTimeRequiredHours) {
            this.strategy = strategy;
            this.preferredTime = preferredTime;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.sprayDurationHours = sprayDurationHours;
            this.dryTimeRequiredHours = dryTimeRequiredHours;
        }
    }

    /**
     * DTO for rainfall summary
     */
    public static class RainfallSummary {
        public double rainfall24hMm;
        public Double hoursSinceSignificantRain;
        public boolean significantRainOccurred;
        public String recommendation;

        public RainfallSummary(double rainfall24hMm, Double hoursSinceSignificantRain) {
            this.rainfall24hMm = rainfall24hMm;
            this.hoursSinceSignificantRain = hoursSinceSignificantRain;
            this.significantRainOccurred = hoursSinceSignificantRain != null && hoursSinceSignificantRain < 24;
            
            if (significantRainOccurred && hoursSinceSignificantRain < 4) {
                this.recommendation = "Splash dispersal event detected. Spray opportunity window open.";
            } else if (significantRainOccurred) {
                this.recommendation = "Recent significant rain. Monitor for infection signs.";
            } else {
                this.recommendation = "No recent significant rain. Maintain preventive schedule.";
            }
        }
    }
}
