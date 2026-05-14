package com.rebenbot.controller;

import com.rebenbot.service.SprayRecommendationService;
import com.rebenbot.service.SprayTimingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * REST controller for spray timing and rainfall tracking endpoints.
 */
@RestController
@RequestMapping("/v1/spray")
@CrossOrigin(origins = "*")
@Slf4j
public class SprayTimingController {

    private final SprayTimingService sprayTimingService;
    private final SprayRecommendationService sprayRecommendationService;

    public SprayTimingController(SprayTimingService sprayTimingService,
                                 SprayRecommendationService sprayRecommendationService) {
        this.sprayTimingService = sprayTimingService;
        this.sprayRecommendationService = sprayRecommendationService;
    }

    /**
     * Get rainfall summary for last 24 hours and spray timing recommendations.
     * 
     * Response includes:
     * - 24-hour cumulative rainfall
     * - Hours since last significant rain (>2mm)
     * - Recommendation based on rainfall patterns
     */
    @GetMapping("/rainfall-summary")
    public ResponseEntity<?> getRainfallSummary() {
        try {
            double rainfall24h = sprayTimingService.calculate24HourRainfall();
            Double hoursSinceRain = sprayTimingService.getHoursSinceLastSignificantRain();
            
            SprayTimingService.RainfallSummary summary = 
                    new SprayTimingService.RainfallSummary(rainfall24h, hoursSinceRain);
            
            log.debug("Rainfall summary: {}mm in 24h, {} hours since significant rain",
                    String.format("%.1f", rainfall24h),
                    hoursSinceRain != null ? String.format("%.1f", hoursSinceRain) : "N/A");
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error calculating rainfall summary", e);
            return ResponseEntity.status(500).body("Error calculating rainfall summary: " + e.getMessage());
        }
    }

    /**
     * Get spray timing window for Peronospora based on weather forecast.
     * 
     * Considers:
     * - Current and upcoming weather
     * - Last significant rain event
     * - Incubation period (weather-dependent)
     * - Spray duration (2-3 hours) and dry time requirements (4 hours)
     * 
     * Strategy types:
     * - PREVENTIVE: No rain imminent, spray at 80% of incubation period
     * - BEFORE_RAIN: Rain forecasted, spray window closes before rain
     * - AFTER_RAIN: Recent rain event detected, spray opportunity window open
     */
    @GetMapping("/window/peronospora")
    public ResponseEntity<?> getSprayWindowPeronospora(
            @RequestParam(required = false, defaultValue = "15.0") Double currentTemperatureC) {
        try {
            // Create a mock weather object for incubation calculation
            // In real scenario, this would use actual current weather
            com.rebenbot.model.WeatherData mockWeather = 
                    com.rebenbot.model.WeatherData.builder()
                            .temperatureC(currentTemperatureC)
                            .build();
            
            double incubationHours = sprayTimingService.calculatePeronosporaIncubationPeriod(mockWeather);
            SprayTimingService.SprayWindow window = sprayTimingService.getOptimalSprayWindow(incubationHours);
            
            log.debug("Peronospora spray window: {} strategy, preferred time: {}", 
                    window.strategy, window.preferredTime);
            
            return ResponseEntity.ok(window);
        } catch (Exception e) {
            log.error("Error calculating spray window", e);
            return ResponseEntity.status(500).body("Error calculating spray window: " + e.getMessage());
        }
    }

    /**
     * Record a spray application for the vineyard.
     * This endpoint should be called when fungicide is applied.
     * 
     * Note: In production, this would need to:
     * - Accept vineyard ID
     * - Update lastSprayDate in database
     * - Log spray details (product used, dosage, area covered)
     */
    @PostMapping("/record")
    public ResponseEntity<?> recordSprayApplication(
            @RequestParam Long vineyardId,
            @RequestParam(required = false) String fungicideProduct) {
        try {
            // This would be implemented to update the Vineyard.lastSprayDate
            // and potentially create a SprayLog entity for historical tracking
            
            log.info("Spray recorded for vineyard {} with product: {}", vineyardId, fungicideProduct);
            
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("message", "Spray application recorded");
            response.put("timestamp", System.currentTimeMillis());
            response.put("vineyardId", vineyardId);
            response.put("fungicide", fungicideProduct);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error recording spray application", e);
            return ResponseEntity.status(500).body("Error recording spray: " + e.getMessage());
        }
    }

    /**
     * Get next-spray recommendation for a vineyard.
     *
     * Uses WBI prognosis risk levels as the primary driver.  The recommended
     * spray interval shrinks as WBI infection risk increases:
     *   > 50 % → 7 days,  25-50 % → 10 days,  < 25 % → 12 days,  none → 14 days.
     *
     * Response includes urgency, target date, allowable window, days until target,
     * whether action is required within 7 days (for browser notifications), and
     * the driving factors behind the recommendation.
     */
    @GetMapping("/recommendation")
    public ResponseEntity<?> getSprayRecommendation(@RequestParam Long vineyardId) {
        try {
            SprayRecommendationService.SprayRecommendation recommendation =
                    sprayRecommendationService.getRecommendation(vineyardId);
            return ResponseEntity.ok(recommendation);
        } catch (Exception e) {
            log.error("Error calculating spray recommendation for vineyard {}", vineyardId, e);
            return ResponseEntity.status(500).body("Error calculating spray recommendation: " + e.getMessage());
        }
    }

}
