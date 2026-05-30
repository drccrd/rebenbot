package com.rebenbot.service;

import com.rebenbot.model.*;
import com.rebenbot.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing fungicide database operations.
 * Handles queries for fungicides, approvals, rotation strategies, and resistance management.
 */
@Service
@Slf4j
public class FungicideService {

    private final FungicideProductRepository fungicideProductRepository;
    private final FracCodeRepository fracCodeRepository;
    private final FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository;
    private final RotationStrategyRepository rotationStrategyRepository;
    private final FungalDiseaseRepository fungalDiseaseRepository;

    public FungicideService(FungicideProductRepository fungicideProductRepository,
                           FracCodeRepository fracCodeRepository,
                           FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository,
                           RotationStrategyRepository rotationStrategyRepository,
                           FungalDiseaseRepository fungalDiseaseRepository) {
        this.fungicideProductRepository = fungicideProductRepository;
        this.fracCodeRepository = fracCodeRepository;
        this.fungicideTargetDiseaseRepository = fungicideTargetDiseaseRepository;
        this.rotationStrategyRepository = rotationStrategyRepository;
        this.fungalDiseaseRepository = fungalDiseaseRepository;
    }

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
        return fungicideProductRepository.findByFracCode(fracCode);
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

    // -----------------------------------------------------------------------
    // CRUD — Fungicide Products
    // -----------------------------------------------------------------------

    @Transactional
    public FungicideProduct saveProduct(FungicideProduct product) {
        return fungicideProductRepository.save(product);
    }

    @Transactional
    public Optional<FungicideProduct> updateProduct(Long id, String name, String activeSubstance,
                                                    Double baseDosageMlHa, Integer phiDays, String fracCode) {
        Optional<FungicideProduct> existing = fungicideProductRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        FungicideProduct p = existing.get();
        if (name != null)                  p.setName(name);
        if (activeSubstance != null)       p.setActiveSubstance(activeSubstance);
        if (baseDosageMlHa != null)        p.setBaseDosageMlHa(baseDosageMlHa);
        if (phiDays != null)               p.setPhiDays(phiDays);
        if (fracCode != null) {
            FracCode fc = fracCodeRepository.findByCode(fracCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown FRAC code: " + fracCode));
            p.setFracCode(fc);
        }
        return Optional.of(fungicideProductRepository.save(p));
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        if (!fungicideProductRepository.existsById(id)) {
            return false;
        }
        fungicideProductRepository.deleteById(id);
        return true;
    }

    // -----------------------------------------------------------------------
    // CRUD — Fungicide Approvals
    // -----------------------------------------------------------------------

    /**
     * Products with a BVL authorisation that expires within the given number of days.
     * Used for renewal warning display in the UI.
     */
    public List<FungicideProduct> getProductsWithExpiringBvlApproval(int daysAhead) {
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead);
        return fungicideProductRepository.findProductsWithExpiringBvlApproval(cutoff);
    }

    // -----------------------------------------------------------------------
    // CRUD — Rotation Strategies
    // -----------------------------------------------------------------------

    @Transactional
    public RotationStrategy saveRotationStrategy(Long diseaseId, String recommendedFracCodes,
                                                  Integer minDays, String description) {
        FungalDisease disease = fungalDiseaseRepository.findById(diseaseId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown disease ID: " + diseaseId));

        RotationStrategy strategy = rotationStrategyRepository.findByDiseaseId(diseaseId)
            .orElse(RotationStrategy.builder().disease(disease).build());

        strategy.setRecommendedFracCodes(recommendedFracCodes);
        if (minDays != null)       strategy.setMinDaysBeforeRepeatingClass(minDays);
        if (description != null)   strategy.setDescription(description);
        return rotationStrategyRepository.save(strategy);
    }

    @Transactional
    public boolean deleteRotationStrategy(Long id) {
        if (!rotationStrategyRepository.existsById(id)) {
            return false;
        }
        rotationStrategyRepository.deleteById(id);
        return true;
    }
}
