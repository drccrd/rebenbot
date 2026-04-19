package com.rebenbot.controller;

import com.rebenbot.repository.FungicideProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private FungicideProductRepository fungicideProductRepository;

    /**
     * Get all available fungicides (redirects to database query).
     * Use /v1/fungicide-management/* for more features (FRAC codes, approvals, etc.).
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllFungicides() {
        log.info("All fungicides requested");
        
        var allProducts = fungicideProductRepository.findAll();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fungicideCount", allProducts.size());
        response.put("fungicides", allProducts.stream()
                .map(p -> Map.of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "activeSubstance", p.getActiveSubstance(),
                    "concentrationPercent", p.getConcentrationPercent(),
                    "manufacturerName", p.getManufacturerName()
                ))
                .toList());

        return ResponseEntity.ok(response);
    }
}
