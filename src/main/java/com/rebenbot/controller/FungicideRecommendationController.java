package com.rebenbot.controller;

import com.rebenbot.repository.FungicideProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * REST API for fungicide recommendations and management.
 * 
 * NOTE: Direct recommendation methods (/recommend, /{disease}) are deprecated.
 * Use /v1/fungicide-management/* endpoints instead for full fungicide database access.
 */
@RestController
@RequestMapping("/v1/fungicides")
@CrossOrigin(origins = "*")
@Slf4j
public class FungicideRecommendationController {

    private final FungicideProductRepository fungicideProductRepository;

    public FungicideRecommendationController(FungicideProductRepository fungicideProductRepository) {
        this.fungicideProductRepository = fungicideProductRepository;
    }

    /**
     * Get all available fungicides (redirects to database query).
     * Use /v1/fungicide-management/* for more features (FRAC codes, approvals, etc.).
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllFungicides() {
        log.debug("All fungicides requested");
        
        var allProducts = fungicideProductRepository.findAll();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fungicideCount", allProducts.size());
        response.put("fungicides", allProducts.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getName());
                    map.put("activeSubstance", p.getActiveSubstance());
                    map.put("concentrationPercent", p.getConcentrationPercent());
                    map.put("manufacturerName", p.getManufacturerName());
                    map.put("baseDosageMlHa", p.getBaseDosageMlHa());
                    map.put("phiDays", p.getPhiDays());
                    return map;
                })
                .toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get latest fungicide recommendations (returns all available fungicides by default).
     * Note: Advanced recommendation logic can be added here based on current risk assessments.
     */
    @GetMapping("/latest-recommendations")
    public ResponseEntity<Map<String, Object>> getLatestRecommendations() {
        log.debug("Latest recommendations requested");
        
        var allProducts = fungicideProductRepository.findAll();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fungicideCount", allProducts.size());
        response.put("fungicides", allProducts.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getName());
                    map.put("activeSubstance", p.getActiveSubstance());
                    map.put("concentrationPercent", p.getConcentrationPercent());
                    map.put("manufacturerName", p.getManufacturerName());
                    map.put("baseDosageMlHa", p.getBaseDosageMlHa());
                    map.put("phiDays", p.getPhiDays());
                    return map;
                })
                .toList());

        return ResponseEntity.ok(response);
    }
}
