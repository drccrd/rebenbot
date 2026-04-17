package com.rebenbot.controller;

import com.rebenbot.model.*;
import com.rebenbot.repository.*;
import com.rebenbot.service.FungicideService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for fungicide database management.
 * Provides access to fungicide products, FRAC codes, approvals, and rotation strategies.
 */
@RestController
@RequestMapping("/v1/fungicide-management")
@CrossOrigin(origins = "*")
@Slf4j
public class FungicideManagementController {

    @Autowired
    private FungicideService fungicideService;

    @Autowired
    private FracCodeRepository fracCodeRepository;

    @Autowired
    private FungicideProductRepository fungicideProductRepository;

    @Autowired
    private FungicideApprovalRepository fungicideApprovalRepository;

    @Autowired
    private RotationStrategyRepository rotationStrategyRepository;

    @Autowired
    private FungalDiseaseRepository fungalDiseaseRepository;

    /**
     * Get all FRAC codes (Fungicide Resistance Action Committee classifications)
     */
    @GetMapping("/frac-codes")
    public ResponseEntity<Map<String, Object>> getAllFracCodes() {
        log.info("All FRAC codes requested");

        List<FracCode> fracCodes = fracCodeRepository.findAll();

        List<Map<String, Object>> codes = fracCodes.stream()
                .map(f -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", f.getId());
                    map.put("code", f.getCode());
                    map.put("chemicalClass", f.getChemicalClass() != null ? f.getChemicalClass() : "");
                    map.put("description", f.getDescription() != null ? f.getDescription() : "");
                    map.put("resistanceRiskLevel", f.getResistanceRiskLevel() != null ? f.getResistanceRiskLevel() : "");
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", codes.size());
        response.put("fracCodes", codes);

        return ResponseEntity.ok(response);
    }

    /**
     * Get fungicides by FRAC code (mode of action)
     */
    @GetMapping("/by-frac/{fracCode}")
    public ResponseEntity<Map<String, Object>> getFungicidesByFracCode(@PathVariable String fracCode) {
        log.info("Fungicides requested for FRAC code: {}", fracCode);

        List<FungicideProduct> fungicides = fungicideService.getFungicidesByFracCode(fracCode);

        if (fungicides.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No fungicides found for FRAC code: " + fracCode
            ));
        }

        List<Map<String, Object>> products = fungicides.stream()
                .map(this::mapFungicideProduct)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fracCode", fracCode);
        response.put("count", products.size());
        response.put("fungicides", products);

        return ResponseEntity.ok(response);
    }

    /**
     * Get fungicides for a specific disease
     */
    @GetMapping("/by-disease/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getFungicidesForDisease(@PathVariable Long diseaseId) {
        log.info("Fungicides requested for disease ID: {}", diseaseId);

        List<FungicideProduct> fungicides = fungicideService.getFungicidesForDisease(diseaseId);

        if (fungicides.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No fungicides found for disease ID: " + diseaseId
            ));
        }

        List<Map<String, Object>> products = fungicides.stream()
                .map(this::mapFungicideProduct)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("count", products.size());
        response.put("fungicides", products);

        return ResponseEntity.ok(response);
    }

    /**
     * Get approval information for a fungicide product
     */
    @GetMapping("/{productId}/approvals")
    public ResponseEntity<Map<String, Object>> getFungicideApprovals(@PathVariable Long productId) {
        log.info("Approvals requested for fungicide product ID: {}", productId);

        Optional<FungicideProduct> product = fungicideProductRepository.findById(productId);
        if (product.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "Fungicide product not found: " + productId
            ));
        }

        List<FungicideApproval> approvals = fungicideApprovalRepository.findByProductId(productId);

        List<Map<String, Object>> approvalList = approvals.stream()
                .map(a -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", a.getId());
                    map.put("region", a.getRegion() != null ? a.getRegion() : "");
                    map.put("approvalValidFrom", a.getApprovalValidFrom() != null ? a.getApprovalValidFrom().toString() : "");
                    map.put("approvalValidUntil", a.getApprovalValidUntil() != null ? a.getApprovalValidUntil().toString() : "");
                    map.put("phiDaysBeforeHarvest", a.getPhiDaysBeforeHarvest() != null ? a.getPhiDaysBeforeHarvest() : 0);
                    map.put("maxDosageMlPer100l", a.getMaxDosageMlPer100l() != null ? a.getMaxDosageMlPer100l() : 0);
                    map.put("approvalStatus", a.getApprovalStatus() != null ? a.getApprovalStatus() : "");
                    map.put("isActive", isApprovalActive(a));
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("productId", productId);
        response.put("productName", product.get().getName());
        response.put("approvalCount", approvalList.size());
        response.put("approvals", approvalList);

        return ResponseEntity.ok(response);
    }

    /**
     * Get rotation strategy recommendations for a disease
     */
    @GetMapping("/rotation-strategy/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getRotationStrategy(@PathVariable Long diseaseId) {
        log.info("Rotation strategy requested for disease ID: {}", diseaseId);

        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);

        if (strategy.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No rotation strategy found for disease ID: " + diseaseId
            ));
        }

        RotationStrategy rs = strategy.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("diseaseName", rs.getDisease() != null ? rs.getDisease().getCommonName() : "");
        response.put("recommendedFracCodes", rs.getRecommendedFracCodes() != null ? 
                rs.getRecommendedFracCodes().split(",") : new String[]{});
        response.put("minDaysBeforeRepeatingClass", rs.getMinDaysBeforeRepeatingClass() != null ? 
                rs.getMinDaysBeforeRepeatingClass() : 14);
        response.put("description", rs.getDescription() != null ? rs.getDescription() : "");

        return ResponseEntity.ok(response);
    }

    /**
     * Get recommended rotation sequence for a disease
     * Returns list of fungicides in rotation order based on FRAC codes
     */
    @GetMapping("/rotation-plan/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getRotationPlan(@PathVariable Long diseaseId) {
        log.info("Rotation plan requested for disease ID: {}", diseaseId);

        Optional<FungalDisease> disease = fungalDiseaseRepository.findById(diseaseId);
        if (disease.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "Disease not found: " + diseaseId
            ));
        }

        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);
        if (strategy.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No rotation strategy found for disease: " + disease.get().getCommonName()
            ));
        }

        RotationStrategy rs = strategy.get();
        String[] fracCodes = rs.getRecommendedFracCodes() != null ? 
                rs.getRecommendedFracCodes().split(",") : new String[]{};

        List<Map<String, Object>> rotationSequence = new ArrayList<>();
        for (String fracCode : fracCodes) {
            fracCode = fracCode.trim();
            List<FungicideProduct> products = fungicideService.getFungicidesByFracCode(fracCode);
            if (!products.isEmpty()) {
                Map<String, Object> step = new HashMap<>();
                step.put("fracCode", fracCode);
                step.put("fungicides", products.stream().map(this::mapFungicideProduct).collect(Collectors.toList()));
                rotationSequence.add(step);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("diseaseName", disease.get().getCommonName());
        response.put("minDaysBetweenRotation", rs.getMinDaysBeforeRepeatingClass() != null ? 
                rs.getMinDaysBeforeRepeatingClass() : 14);
        response.put("rotationSequence", rotationSequence);

        return ResponseEntity.ok(response);
    }

    // Helper methods

    private Map<String, Object> mapFungicideProduct(FungicideProduct p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("activeSubstance", p.getActiveSubstance());
        map.put("concentration", p.getConcentrationPercent());
        map.put("manufacturer", p.getManufacturerName());
        map.put("fracCode", p.getFracCode() != null ? p.getFracCode().getCode() : "");
        map.put("fracDescription", p.getFracCode() != null ? p.getFracCode().getDescription() : "");
        return map;
    }

    private boolean isApprovalActive(FungicideApproval approval) {
        if ("EXPIRED".equals(approval.getApprovalStatus())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (approval.getApprovalValidUntil() != null && approval.getApprovalValidUntil().isBefore(today)) {
            return false;
        }
        return "ACTIVE".equals(approval.getApprovalStatus());
    }
}
