package com.rebenbot.service;

import com.rebenbot.model.FungicideProduct;
import com.rebenbot.model.InfectionRisk;
import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommends optimal fungicides based on disease type, risk level, and weather conditions.
 * Considers approval status, pre-harvest intervals (PHI), and application effectiveness.
 */
@Service
@Slf4j
public class FungicideRecommendationService {

    @Autowired
    private FungicideProductRepository fungicideProductRepository;

    @Autowired
    private InfectionRiskRepository infectionRiskRepository;

    /**
     * Data class for fungicide recommendations with rationale
     */
    public static class FungicideRecommendation {
        public final FungicideProduct fungicide;
        public final double score;  // 0.0-1.0, higher = better match
        public final String rationale;
        public final int daysUntilHarvest;
        public final boolean applicable;  // True if PHI allows application
        public final String timing;  // When to spray (immediate, within 24h, etc.)

        public FungicideRecommendation(FungicideProduct fungicide, double score, String rationale, 
                                      int daysUntilHarvest, boolean applicable, String timing) {
            this.fungicide = fungicide;
            this.score = score;
            this.rationale = rationale;
            this.daysUntilHarvest = daysUntilHarvest;
            this.applicable = applicable;
            this.timing = timing;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", fungicide.getId());
            map.put("name", fungicide.getName());
            map.put("activeSubstance", fungicide.getActiveSubstance());
            map.put("manufacturerName", fungicide.getManufacturerName());
            map.put("concentrationPercent", fungicide.getConcentrationPercent());
            map.put("score", String.format("%.2f", score));
            map.put("rationale", rationale);
            map.put("daysUntilHarvest", daysUntilHarvest);
            map.put("applicable", applicable);
            map.put("timing", timing);
            return map;
        }
    }

    /**
     * Get fungicide recommendations for a specific disease and risk level.
     * Assumes harvest 60 days from now (generic for viticulture).
     *
     * @param diseaseCommonName e.g., "Peronospora" or "Oidium"
     * @param riskScore 0.0-1.0
     * @return List of fungicides ranked by suitability
     */
    public List<FungicideRecommendation> recommendForDisease(String diseaseCommonName, double riskScore) {
        return recommendForDisease(diseaseCommonName, riskScore, 60);  // 60 days to harvest
    }

    /**
     * Get fungicide recommendations with custom days to harvest.
     *
     * @param diseaseCommonName Disease common name
     * @param riskScore Current risk score (0.0-1.0)
     * @param daysUntilHarvest Days until harvest (affects PHI filtering)
     * @return Sorted list of recommendations
     */
    public List<FungicideRecommendation> recommendForDisease(String diseaseCommonName, 
                                                             double riskScore, int daysUntilHarvest) {
        // NOTE: Schema changed from simple Fungicide to FungicideProduct + FungicideTargetDisease.
        // This method needs refactoring to work with the new relationships.
        // For now, return empty list. Use FungicideService or FungicideManagementController instead.
        log.warn("FungicideRecommendationService.recommendForDisease() needs schema refactoring. Use FungicideManagementController instead.");
        return Collections.emptyList();
    }

    /**
     * Score a fungicide based on disease type, risk level, and applicability
     */
    private FungicideRecommendation scoreAndRecommend(FungicideProduct fungicide, String disease, 
                                                      double riskScore, int daysUntilHarvest, 
                                                      String timing, boolean isApprovalActive,
                                                      int minDaysBeforeHarvest) {
        // Check PHI applicability
        boolean applicable = daysUntilHarvest >= minDaysBeforeHarvest;

        // Base score: how well suited is this fungicide for current risk?
        double baseScore = scoreByDisease(fungicide, disease, riskScore);
        
        if (!isApprovalActive) {
            baseScore *= 0.7;  // Penalize expired approvals
        }
        if (!applicable) {
            baseScore *= 0.5;  // Penalize if PHI violated
        }

        String rationale = buildRationale(fungicide, disease, riskScore, applicable, daysUntilHarvest, minDaysBeforeHarvest);

        return new FungicideRecommendation(
                fungicide, 
                baseScore, 
                rationale, 
                daysUntilHarvest, 
                applicable, 
                timing
        );
    }

    /**
     * Score fungicide based on disease-specific characteristics
     */
    private double scoreByDisease(FungicideProduct f, String disease, double riskScore) {
        double score = 0.5;  // Base score

        // Risk-level based scoring: higher risk = prefer systemic fungicides
        if (riskScore >= 0.75) {  // CRITICAL
            score = isSystemic(f) ? 0.95 : 0.80;
        } else if (riskScore >= 0.50) {  // HIGH
            score = isSystemic(f) ? 0.90 : 0.75;
        } else if (riskScore >= 0.25) {  // MEDIUM
            score = isSystemic(f) ? 0.85 : 0.70;
        } else {  // LOW/NONE - preventive is acceptable
            score = 0.70;
        }

        // Disease-specific adjustments
        if ("Peronospora".equals(disease)) {
            // Prefer contact + systemic combos for Peronospora
            if ("Ridomil Gold Combi".equals(f.getName())) score += 0.05;
            if ("Aliette WG".equals(f.getName())) score += 0.03;
            // Copper is good for late season
            if ("Cuproxat 50".equals(f.getName()) && riskScore < 0.5) score += 0.05;
        } else if ("Oidium".equals(disease)) {
            // For Oidium, sulfur is classic preventive, systemics for high risk
            if ("Netzschwefel".equals(f.getName()) && riskScore < 0.5) score += 0.05;
            if (isSystemic(f) && riskScore >= 0.5) score += 0.05;
        }

        return Math.min(1.0, score);  // Cap at 1.0
    }

    /**
     * Determine spray urgency based on risk level
     */
    private String getSprayTiming(double riskScore) {
        if (riskScore >= 0.75) {
            return "IMMEDIATE - Spray today";
        } else if (riskScore >= 0.50) {
            return "URGENT - Spray within 24 hours";
        } else if (riskScore >= 0.25) {
            return "PLANNED - Spray within 48-72 hours if conditions persist";
        } else {
            return "PREVENTIVE - Monitor conditions, spray only if risk increases";
        }
    }

    /**
     * Check if fungicide approval is currently valid
     */
    private boolean isApprovalActive(LocalDateTime now) {
        return true;  // In future, check fungicide.approvalValidUntil
    }

    /**
     * Determine if fungicide is systemic (transported within plant)
     */
    private boolean isSystemic(FungicideProduct f) {
        String substance = f.getActiveSubstance().toLowerCase();
        // Systemic active substances
        return substance.contains("mefenoxam") 
                || substance.contains("fosetyl") 
                || substance.contains("triadimefon")
                || substance.contains("bupirimate")
                || substance.contains("quinoxyfen");
    }

    /**
     * Build human-readable rationale for the recommendation
     */
    private String buildRationale(FungicideProduct f, String disease, double riskScore, 
                                  boolean applicable, int daysUntilHarvest, int minDaysBeforeHarvest) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Recommended for ").append(disease);
        
        if (riskScore >= 0.75) {
            sb.append(" at CRITICAL risk. ");
            sb.append(isSystemic(f) ? "Systemic action provides rapid control." 
                                    : "Contact protection for immediate application.");
        } else if (riskScore >= 0.50) {
            sb.append(" at HIGH risk. Effective preventive and curative action.");
        } else if (riskScore >= 0.25) {
            sb.append(" at MEDIUM risk. Good preventive protection.");
        } else {
            sb.append(" for preventive monitoring.");
        }
        
        if (!applicable) {
            sb.append(" ⚠ PHI ISSUE: Harvest in ").append(daysUntilHarvest).append(" days, ")
                    .append("but ").append(f.getName()).append(" requires ").append(minDaysBeforeHarvest)
                    .append(" days. DO NOT APPLY.");
        } else if (daysUntilHarvest < minDaysBeforeHarvest + 7) {
            sb.append(" PHI is tight: ").append(daysUntilHarvest).append(" days to harvest.");
        }
        
        return sb.toString();
    }

    /**
     * Get all recommendations for the latest risk assessment
     */
    public Map<String, Object> getLatestRecommendations(int daysUntilHarvest) {
        List<InfectionRisk> latestRisks = infectionRiskRepository.findAll().stream()
                .sorted(Comparator.comparing(InfectionRisk::getAssessedAt).reversed())
                .limit(2)  // Get latest Peronospora and Oidium
                .collect(Collectors.toList());

        if (latestRisks.isEmpty()) {
            return Map.of("status", "NO_DATA", "message", "No risk assessments found. Run weather fetch and risk assessment first.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("assessmentTime", latestRisks.get(0).getAssessedAt());
        result.put("daysUntilHarvest", daysUntilHarvest);

        Map<String, List<Map<String, Object>>> recommendations = new HashMap<>();
        
        for (InfectionRisk risk : latestRisks) {
            String disease = risk.getDisease().getCommonName();
            List<FungicideRecommendation> recs = recommendForDisease(disease, risk.getRiskScore(), daysUntilHarvest);
            recommendations.put(disease, 
                    recs.stream().map(FungicideRecommendation::toMap).collect(Collectors.toList()));
        }

        result.put("recommendations", recommendations);
        result.put("totalRecommendations", recommendations.values().stream().mapToInt(List::size).sum());
        
        return result;
    }
}
