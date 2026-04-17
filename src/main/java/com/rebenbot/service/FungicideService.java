package com.rebenbot.service;

import com.rebenbot.model.*;
import com.rebenbot.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing fungicide database operations.
 * Handles queries for fungicides, approvals, rotation strategies, and resistance management.
 */
@Service
@Slf4j
public class FungicideService {

    @Autowired
    private FungicideProductRepository fungicideProductRepository;

    @Autowired
    private FracCodeRepository fracCodeRepository;

    @Autowired
    private FungicideApprovalRepository fungicideApprovalRepository;

    @Autowired
    private FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository;

    @Autowired
    private RotationStrategyRepository rotationStrategyRepository;

    @Autowired
    private FungalDiseaseRepository fungalDiseaseRepository;

    /**
     * Get all fungicides that target a specific disease
     */
    public List<FungicideProduct> getFungicidesForDisease(Long diseaseId) {
        List<FungicideTargetDisease> targetDiseases = fungicideTargetDiseaseRepository.findByDiseaseId(diseaseId);
        return targetDiseases.stream()
            .map(FungicideTargetDisease::getProduct)
            .collect(Collectors.toList());
    }

    /**
     * Get all fungicides in a specific FRAC code (mode of action)
     */
    public List<FungicideProduct> getFungicidesByFracCode(String fracCode) {
        Optional<FracCode> frac = fracCodeRepository.findByCode(fracCode);
        if (frac.isEmpty()) {
            return new ArrayList<>();
        }
        return fungicideProductRepository.findAll().stream()
            .filter(f -> f.getFracCode().equals(frac.get()))
            .collect(Collectors.toList());
    }

    /**
     * Get active approvals for a region
     */
    public List<FungicideApproval> getActiveApprovalsForRegion(String region) {
        return fungicideApprovalRepository.findActiveApprovalsByRegion(region);
    }

    /**
     * Get approval info for a specific product in a region
     */
    public Optional<FungicideApproval> getApprovalForProductInRegion(Long productId, String region) {
        return fungicideApprovalRepository.findByProductIdAndRegion(productId, region);
    }

    /**
     * Get the recommended rotation strategy for a disease
     */
    public Optional<RotationStrategy> getRotationStrategyForDisease(Long diseaseId) {
        return rotationStrategyRepository.findByDiseaseId(diseaseId);
    }

    /**
     * Get rotation strategy as list of FRAC codes
     */
    public List<String> getRecommendedFracRotation(Long diseaseId) {
        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);
        if (strategy.isEmpty()) {
            return new ArrayList<>();
        }
        String fracCodes = strategy.get().getRecommendedFracCodes();
        return Arrays.asList(fracCodes.split(","));
    }

    /**
     * Validate if a proposed spray sequence follows good rotation practices
     * Returns true if rotation is valid (uses different FRAC codes appropriately)
     */
    public boolean validateRotationSequence(Long diseaseId, List<Long> proposedFungicideIds) {
        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);
        if (strategy.isEmpty()) {
            log.warn("No rotation strategy found for disease: {}", diseaseId);
            return true;  // If no strategy defined, allow
        }

        List<String> recommendedFracs = getRecommendedFracRotation(diseaseId);
        if (recommendedFracs.isEmpty()) {
            return true;
        }

        // Check each proposed fungicide's FRAC code against the recommended sequence
        for (Long fungicideId : proposedFungicideIds) {
            Optional<FungicideProduct> product = fungicideProductRepository.findById(fungicideId);
            if (product.isPresent()) {
                String fracCode = product.get().getFracCode().getCode();
                if (!recommendedFracs.contains(fracCode)) {
                    log.warn("Fungicide {} uses FRAC code {} which is not in recommended rotation: {}",
                        product.get().getName(), fracCode, recommendedFracs);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get all fungicides for a disease with their approval info for a region
     */
    public List<Map<String, Object>> getFungicidesWithApprovalsForDiseaseAndRegion(Long diseaseId, String region) {
        List<FungicideProduct> fungicides = getFungicidesForDisease(diseaseId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (FungicideProduct fungicide : fungicides) {
            Optional<FungicideApproval> approval = getApprovalForProductInRegion(fungicide.getId(), region);
            if (approval.isPresent() && approval.get().getApprovalStatus() == FungicideApproval.ApprovalStatus.ACTIVE) {
                Map<String, Object> item = new HashMap<>();
                item.put("product", fungicide);
                item.put("approval", approval.get());
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Get all FRAC codes
     */
    public List<FracCode> getAllFracCodes() {
        return fracCodeRepository.findAll();
    }

    /**
     * Get all fungicide products
     */
    public List<FungicideProduct> getAllFungicides() {
        return fungicideProductRepository.findAll();
    }

    /**
     * Get all fungicide target diseases (all product-disease relationships)
     */
    public List<FungicideTargetDisease> getAllFungicideTargetDiseases() {
        return fungicideTargetDiseaseRepository.findAll();
    }
}
