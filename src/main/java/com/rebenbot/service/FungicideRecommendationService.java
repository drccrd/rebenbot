package com.rebenbot.service;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.FungicideProduct;
import com.rebenbot.model.FungicideTargetDisease;
import com.rebenbot.model.InfectionRisk;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.repository.FungicideTargetDiseaseRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommends optimal fungicides based on disease type, risk level, and PHI.
 * Reads product and approval data from the database, so recommendations
 * automatically reflect the latest BVL approval status.
 */
@Service
@Slf4j
public class FungicideRecommendationService {

    private final FungicideProductRepository fungicideProductRepository;
    private final FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository;
    private final FungalDiseaseRepository fungalDiseaseRepository;
    private final InfectionRiskRepository infectionRiskRepository;

    public FungicideRecommendationService(FungicideProductRepository fungicideProductRepository,
                                         FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository,
                                         FungalDiseaseRepository fungalDiseaseRepository,
                                         InfectionRiskRepository infectionRiskRepository) {
        this.fungicideProductRepository = fungicideProductRepository;
        this.fungicideTargetDiseaseRepository = fungicideTargetDiseaseRepository;
        this.fungalDiseaseRepository = fungalDiseaseRepository;
        this.infectionRiskRepository = infectionRiskRepository;
    }

    /**
     * Data class for fungicide recommendations with rationale.
     */
    public static class FungicideRecommendation {
        public final FungicideProduct fungicide;
        public final double score;
        public final String rationale;
        public final int daysUntilHarvest;
        public final boolean applicable;
        public final String timing;

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
            map.put("fracCode", fungicide.getFracCode() != null ? fungicide.getFracCode().getCode() : "");
            map.put("fracDescription", fungicide.getFracCode() != null ? fungicide.getFracCode().getDescription() : "");
            map.put("resistanceRisk", fungicide.getFracCode() != null && fungicide.getFracCode().getResistanceRiskLevel() != null
                    ? fungicide.getFracCode().getResistanceRiskLevel().name() : "");
            map.put("score", String.format("%.2f", score));
            map.put("rationale", rationale);
            map.put("daysUntilHarvest", daysUntilHarvest);
            map.put("applicable", applicable);
            map.put("timing", timing);
            return map;
        }
    }

    public List<FungicideRecommendation> recommendForDisease(String diseaseCommonName, double riskScore) {
        return recommendForDisease(diseaseCommonName, riskScore, 60);
    }

    /**
     * Recommend fungicides for a disease at a given risk level.
     * Only returns products with an active German approval; expired approvals are filtered out.
     */
    public List<FungicideRecommendation> recommendForDisease(String diseaseCommonName,
                                                             double riskScore, int daysUntilHarvest) {
        FungalDisease disease = fungalDiseaseRepository.findByCommonName(diseaseCommonName);
        if (disease == null) {
            log.warn("Unknown disease: {}", diseaseCommonName);
            return Collections.emptyList();
        }

        List<FungicideTargetDisease> targets = fungicideTargetDiseaseRepository.findByDiseaseId(disease.getId());
        String timing = getSprayTiming(riskScore);

        List<FungicideRecommendation> recommendations = new ArrayList<>();

        for (FungicideTargetDisease target : targets) {
            FungicideProduct product = target.getProduct();

            // Check German BVL approval — skip products not confirmed by BVL
            boolean approvalActive = Boolean.TRUE.equals(product.getBvlApprovedInGermany());
            if (!approvalActive) {
                log.debug("Skipping {} — BVL approval not confirmed", product.getName());
                continue;
            }

            int phi = product.getPhiDays() != null ? product.getPhiDays() : 0;
            boolean phiOk = daysUntilHarvest >= phi;

            double score = scoreProduct(product, target, diseaseCommonName, riskScore, approvalActive, phiOk);
            String rationale = buildRationale(product, target, diseaseCommonName, riskScore, phiOk, daysUntilHarvest, phi);

            recommendations.add(new FungicideRecommendation(product, score, rationale, daysUntilHarvest, phiOk, timing));
        }

        recommendations.sort(Comparator.comparingDouble((FungicideRecommendation r) -> r.score).reversed());
        return recommendations;
    }

    private double scoreProduct(FungicideProduct f, FungicideTargetDisease target,
                                String disease, double riskScore,
                                boolean approvalActive, boolean phiOk) {
        double base = 0.5;

        // At high risk, boost systemic/curative products
        if (riskScore >= 0.75 && isSystemic(f)) base = Math.min(1.0, base + 0.15);
        else if (riskScore < 0.25) base = Math.min(1.0, base + 0.10); // preventive fine for low risk

        // Penalise if PHI is violated
        if (!phiOk) base *= 0.4;

        // Penalise HIGH resistance risk at high-frequency use — nudge towards rotation
        if (f.getFracCode() != null && f.getFracCode().getResistanceRiskLevel() != null) {
            if (f.getFracCode().getResistanceRiskLevel().name().equals("HIGH") && riskScore < 0.5) {
                base *= 0.85; // save high-risk products for when needed
            }
        }

        return Math.min(1.0, base);
    }

    private String getSprayTiming(double riskScore) {
        if (riskScore >= 0.75) return "IMMEDIATE — Spray today";
        if (riskScore >= 0.50) return "URGENT — Spray within 24 hours";
        if (riskScore >= 0.25) return "PLANNED — Spray within 48–72 hours if conditions persist";
        return "PREVENTIVE — Monitor conditions";
    }

    private boolean isSystemic(FungicideProduct f) {
        if (f.getFracCode() == null) return false;
        String code = f.getFracCode().getCode();
        // Contact codes: M1, M2, M4 — everything else is systemic or locally systemic
        return !code.startsWith("M");
    }

    private String buildRationale(FungicideProduct f, FungicideTargetDisease target,
                                  String disease, double riskScore,
                                  boolean phiOk, int daysUntilHarvest, int phi) {
        StringBuilder sb = new StringBuilder();

        sb.append(f.getName()).append(" (").append(f.getActiveSubstance()).append(")");
        if (f.getFracCode() != null) {
            sb.append(" FRAC ").append(f.getFracCode().getCode());
        }
        sb.append(" — ");

        if (riskScore >= 0.75)      sb.append("CRITICAL risk: curative action required. ");
        else if (riskScore >= 0.50) sb.append("HIGH risk: preventive + curative. ");
        else if (riskScore >= 0.25) sb.append("MEDIUM risk: good preventive protection. ");
        else                        sb.append("LOW risk: preventive only. ");

        if (!phiOk) {
            sb.append("⚠ PHI VIOLATION: harvest in ").append(daysUntilHarvest)
              .append(" days but PHI is ").append(phi).append(" days — DO NOT APPLY.");
        } else if (daysUntilHarvest < phi + 7) {
            sb.append("PHI tight: ").append(daysUntilHarvest).append(" days to harvest.");
        }

        return sb.toString().trim();
    }

    /**
     * Get all recommendations for the latest risk assessment.
     */
    public Map<String, Object> getLatestRecommendations(int daysUntilHarvest) {
        List<InfectionRisk> latestRisks = infectionRiskRepository.findLatestRisksByAllDiseases();

        if (latestRisks.isEmpty()) {
            return Map.of("status", "NO_DATA",
                    "message", "No risk assessments found. Run weather fetch and risk assessment first.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("assessmentTime", latestRisks.get(0).getAssessedAt());
        result.put("daysUntilHarvest", daysUntilHarvest);

        Map<String, List<Map<String, Object>>> recommendations = new LinkedHashMap<>();
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
