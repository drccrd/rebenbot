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
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", "Internal server error"));
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
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", "Internal server error"));
        }
    }

}
