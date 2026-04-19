package com.rebenbot.controller;

import com.rebenbot.controller.dto.SprayApplicationRequest;
import com.rebenbot.model.SprayApplication;
import com.rebenbot.service.SprayApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/spray-diary")
@CrossOrigin(origins = "*")
@Slf4j
public class SprayApplicationController {

    private final SprayApplicationService sprayApplicationService;

    public SprayApplicationController(SprayApplicationService sprayApplicationService) {
        this.sprayApplicationService = sprayApplicationService;
    }

    /**
     * Log a spray application.
     * 
     * Request body:
     * {
     *   "vineyardId": 1,
     *   "fungicideId": 5,
     *   "diseaseId": 1,
     *   "applicationDate": "2026-04-16T14:30:00",
     *   "growthStageBbch": "75",
     *   "temperatureC": 18.5,
     *   "humidityPercent": 65.0,
     *   "windSpeedMsec": 2.5,
     *   "notes": "Applied in afternoon, clear conditions"
     * }
     */
    @PostMapping("/record")
    public ResponseEntity<?> recordSpray(@Valid @RequestBody SprayApplicationRequest request) {
        try {
            SprayApplication spray = sprayApplicationService.recordSpray(
                    request.getVineyardId(), 
                    request.getFungicideId(), 
                    request.getDiseaseId(), 
                    request.getApplicationDate(), 
                    request.getGrowthStageBbch(), 
                    request.getTemperatureC(), 
                    request.getHumidityPercent(), 
                    request.getWindSpeedMsec(), 
                    request.getNotes());

            // Assess effectiveness after recording
            sprayApplicationService.assessSprayEffectiveness(spray);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Spray application recorded",
                    "spray", sprayToMap(spray)
            ));
        } catch (Exception e) {
            log.error("Error recording spray:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get spray history for a vineyard.
     * Optional query params: ?lastDays=30
     */
    @GetMapping("/history/{vineyardId}")
    public ResponseEntity<?> getSprayHistory(
            @PathVariable Long vineyardId,
            @RequestParam(required = false) Integer lastDays) {
        try {
            List<SprayApplication> sprays = sprayApplicationService.getSprayHistory(vineyardId, lastDays);
            List<Map<String, Object>> sprayMaps = sprays.stream()
                    .map(this::sprayToMap)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "vineyardId", vineyardId,
                    "sprayCount", sprays.size(),
                    "lastDays", lastDays != null ? lastDays : "all",
                    "sprays", sprayMaps
            ));
        } catch (Exception e) {
            log.error("Error fetching spray history:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get recent sprays (last 7 days) for dashboard display.
     */
    @GetMapping("/recent/{vineyardId}")
    public ResponseEntity<?> getRecentSprays(@PathVariable Long vineyardId) {
        try {
            List<SprayApplication> sprays = sprayApplicationService.getRecentSprays(vineyardId);
            List<Map<String, Object>> sprayMaps = sprays.stream()
                    .map(this::sprayToMap)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "recentSprayCount", sprays.size(),
                    "sprays", sprayMaps
            ));
        } catch (Exception e) {
            log.error("Error fetching recent sprays:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get spray frequency analysis (how often each fungicide is used).
     */
    @GetMapping("/frequency/{vineyardId}")
    public ResponseEntity<?> getFrequencyAnalysis(
            @PathVariable Long vineyardId,
            @RequestParam(required = false) Integer lastDays) {
        try {
            Map<String, Integer> frequency = sprayApplicationService.getSprayFrequencyAnalysis(vineyardId, lastDays);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "period", lastDays != null ? lastDays + " days" : "all time",
                    "frequencyByFungicide", frequency
            ));
        } catch (Exception e) {
            log.error("Error fetching frequency analysis:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Helper to convert spray application to map.
     */
    private Map<String, Object> sprayToMap(SprayApplication spray) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", spray.getId());
        result.put("applicationDate", spray.getApplicationDate().toString());
        result.put("fungicide", spray.getFungicideProduct().getName());
        result.put("disease", spray.getDisease().getCommonName());
        result.put("growthStageBbch", spray.getGrowthStageBbch() != null ? spray.getGrowthStageBbch() : "N/A");
        result.put("dosageLitersPerAre", String.format("%.2f", spray.getDosageLitersPerAre()));
        result.put("temperatureC", spray.getTemperatureC() != null ? spray.getTemperatureC() : "N/A");
        result.put("humidityPercent", spray.getHumidityPercent() != null ? spray.getHumidityPercent() : "N/A");
        result.put("windSpeedMsec", spray.getWindSpeedMsec() != null ? spray.getWindSpeedMsec() : "N/A");
        result.put("notes", spray.getNotes() != null ? spray.getNotes() : "");
        result.put("efficacyAssessment", spray.getEfficacyAssessment() != null ? 
                String.format("%.0f%%", spray.getEfficacyAssessment() * 100) : "Pending");
        result.put("efficacyNotes", spray.getEfficacyNotes() != null ? spray.getEfficacyNotes() : "");
        return result;
    }
}
