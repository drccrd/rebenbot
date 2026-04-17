package com.rebenbot.controller;

import com.rebenbot.service.FungicideRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * REST API for fungicide recommendations based on disease risk assessments.
 */
@RestController
@RequestMapping("/v1/fungicides")
@CrossOrigin(origins = "*")
@Slf4j
public class FungicideRecommendationController {

    @Autowired
    private FungicideRecommendationService recommendationService;

    /**
     * Get all available fungicides.
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllFungicides() {
        log.info("All fungicides requested");

        List<FungicideRecommendationService.FungicideRecommendation> allRecommendations = 
                recommendationService.recommendForDisease("Peronospora", 0.5);
        allRecommendations.addAll(recommendationService.recommendForDisease("Oidium", 0.5));

        // Remove duplicates
        Set<Long> seen = new HashSet<>();
        List<FungicideRecommendationService.FungicideRecommendation> unique = new ArrayList<>();
        for (var rec : allRecommendations) {
            Long productId = ((Number) rec.toMap().get("productId")).longValue();
            if (seen.add(productId)) {
                unique.add(rec);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fungicideCount", unique.size());
        response.put("fungicides", unique.stream()
                .map(FungicideRecommendationService.FungicideRecommendation::toMap)
                .toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get fungicide recommendations for a specific disease and risk score.
     * 
     * @param disease Disease common name (e.g., "Peronospora", "Oidium")
     * @param riskScore Risk score 0.0-1.0
     * @param daysUntilHarvest Days until harvest (for PHI calculation), default 60
     * @return List of recommended fungicides ranked by suitability
     */
    @GetMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommendFungicides(
            @RequestParam String disease,
            @RequestParam double riskScore,
            @RequestParam(required = false, defaultValue = "60") int daysUntilHarvest) {

        log.info("Fungicide recommendation requested: disease={}, riskScore={}, daysToHarvest={}", 
                disease, String.format("%.2f", riskScore), daysUntilHarvest);

        if (riskScore < 0 || riskScore > 1.0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Risk score must be between 0.0 and 1.0"
            ));
        }

        List<FungicideRecommendationService.FungicideRecommendation> recommendations = 
                recommendationService.recommendForDisease(disease, riskScore, daysUntilHarvest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("disease", disease);
        response.put("riskScore", String.format("%.2f", riskScore));
        response.put("daysUntilHarvest", daysUntilHarvest);
        response.put("recommendationCount", recommendations.size());
        response.put("recommendations", recommendations.stream()
                .map(FungicideRecommendationService.FungicideRecommendation::toMap)
                .toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get comprehensive fungicide recommendations based on latest risk assessment.
     * Recommends optimal products for all diseases with current high risk levels.
     * 
     * @param daysUntilHarvest Days until harvest for PHI calculation, default 60
     * @return Fungicide recommendations organized by disease
     */
    @GetMapping("/latest-recommendations")
    public ResponseEntity<Map<String, Object>> getLatestRecommendations(
            @RequestParam(required = false, defaultValue = "60") int daysUntilHarvest) {

        log.info("Latest fungicide recommendations requested (daysToHarvest={})", daysUntilHarvest);

        Map<String, Object> result = recommendationService.getLatestRecommendations(daysUntilHarvest);
        
        if ("NO_DATA".equals(result.get("status"))) {
            return ResponseEntity.status(404).body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get all available fungicides for a specific disease.
     * 
     * @param disease Disease common name (e.g., "Peronospora", "Oidium")
     * @return List of all fungicides targeting this disease
     */
    @GetMapping("/{disease}")
    public ResponseEntity<Map<String, Object>> getFungicidesForDisease(@PathVariable String disease) {
        log.info("Fungicides requested for disease: {}", disease);

        List<FungicideRecommendationService.FungicideRecommendation> recommendations = 
                recommendationService.recommendForDisease(disease, 0.5);  // Neutral risk for all fungicides

        if (recommendations.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No fungicides found for disease: " + disease
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("disease", disease);
        response.put("fungicideCount", recommendations.size());
        response.put("fungicides", recommendations.stream()
                .map(FungicideRecommendationService.FungicideRecommendation::toMap)
                .toList());

        return ResponseEntity.ok(response);
    }
}
