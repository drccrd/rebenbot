package com.rebenbot.service;

import com.rebenbot.model.*;
import com.rebenbot.repository.SprayApplicationRepository;
import com.rebenbot.repository.VineyardRepository;
import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing spray applications and calculating dosages.
 */
@Service
@Slf4j
public class SprayApplicationService {

    private final SprayApplicationRepository sprayApplicationRepository;
    private final VineyardRepository vineyardRepository;
    private final FungicideProductRepository fungicideProductRepository;
    private final FungalDiseaseRepository diseaseRepository;
    private final InfectionRiskRepository infectionRiskRepository;

    public SprayApplicationService(SprayApplicationRepository sprayApplicationRepository,
                                   VineyardRepository vineyardRepository,
                                   FungicideProductRepository fungicideProductRepository,
                                   FungalDiseaseRepository diseaseRepository,
                                   InfectionRiskRepository infectionRiskRepository) {
        this.sprayApplicationRepository = sprayApplicationRepository;
        this.vineyardRepository = vineyardRepository;
        this.fungicideProductRepository = fungicideProductRepository;
        this.diseaseRepository = diseaseRepository;
        this.infectionRiskRepository = infectionRiskRepository;
    }

    /**
     * Log a spray application to the diary.
     */
    public SprayApplication recordSpray(Long vineyardId, Long fungicideId, Long diseaseId,
                                       LocalDateTime applicationDate, String growthStageBbch,
                                       Double temperatureC, Double humidityPercent, Double windSpeedMsec,
                                       String notes) {
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found: " + vineyardId));
        
        FungicideProduct fungicideProduct = fungicideProductRepository.findById(fungicideId)
                .orElseThrow(() -> new RuntimeException("Fungicide not found: " + fungicideId));
        
        FungalDisease disease = diseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new RuntimeException("Disease not found: " + diseaseId));

        // Calculate dosage in liters per are (vineyard is in ares)
        double dosageLitersPerAre = calculateDosage(fungicideProduct, vineyard.getSizeAres(), growthStageBbch);

        SprayApplication spray = SprayApplication.builder()
                .vineyard(vineyard)
                .fungicideProduct(fungicideProduct)
                .disease(disease)
                .applicationDate(applicationDate)
                .growthStageBbch(growthStageBbch)
                .dosageLitersPerAre(dosageLitersPerAre)
                .temperatureC(temperatureC)
                .humidityPercent(humidityPercent)
                .windSpeedMsec(windSpeedMsec)
                .notes(notes)
                .build();

        SprayApplication saved = sprayApplicationRepository.save(spray);
        log.info("Recorded spray: {} on vineyard {} for {} using {}", 
                applicationDate, vineyardId, disease.getCommonName(), fungicideProduct.getName());

        return saved;
    }

    /**
     * Calculate fungicide dosage based on product specifications and vineyard size.
     * 
     * Formula: Base dosage (ml/100L) * water volume needed for vineyard size
     * Standard: 1000L per hectare (100L per 10 ares)
     * Growth stage can affect dosage (e.g., early season vs. pre-harvest)
     */
    public double calculateDosage(FungicideProduct fungicideProduct, double vineyardSizeAres, String growthStageBbch) {
        // Base dosage from fungicide (typically ml per 100L of water)
        // Note: May need to fetch from FungicideApproval for region-specific max dosage
        double baseDosagePerHundredLiters = 500.0;  // Default, should be from FungicideApproval
        
        // Standard water volume: 1000L per hectare = 100L per 10 ares
        double waterPerAre = 10.0;  // liters per are
        double totalWaterVolume = vineyardSizeAres * waterPerAre;
        
        // Scale base dosage to total water volume
        double dosageLiters = (baseDosagePerHundredLiters / 100.0) * (totalWaterVolume / 100.0);
        
        // Growth stage adjustment: pre-harvest doses can be lower due to lower disease pressure
        if (growthStageBbch != null && (growthStageBbch.startsWith("80") || growthStageBbch.startsWith("81"))) {
            // Veraison (80) or post-veraison (81): Peronospora risk drops significantly
            dosageLiters *= 0.8;  // 20% reduction
        }
        
        log.info("Calculated dosage: {:.2f}L for {} ares, fungicide: {}, growth stage: {}", 
                dosageLiters, vineyardSizeAres, fungicideProduct.getName(), growthStageBbch);
        
        return dosageLiters;
    }

    /**
     * Get spray history for a vineyard.
     */
    public List<SprayApplication> getSprayHistory(Long vineyardId, Integer lastDays) {
        if (lastDays != null && lastDays > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(lastDays);
            return sprayApplicationRepository.findByVineyardIdAndApplicationDateBetweenOrderByApplicationDateDesc(
                    vineyardId, cutoff, LocalDateTime.now());
        }
        return sprayApplicationRepository.findByVineyardIdOrderByApplicationDateDesc(vineyardId);
    }

    /**
     * Assess spray effectiveness by comparing spray timing to infection risk at that time.
     * 
     * Returns a score 0-1 where:
     * - 1.0: Perfect timing (sprayed just before high risk period)
     * - 0.5: Moderate timing (sprayed during moderate risk)
     * - 0.0: Poor timing (sprayed when risk was very low)
     */
    public void assessSprayEffectiveness(SprayApplication spray) {
        // Find the closest risk assessment to the spray application date
        List<InfectionRisk> risks = infectionRiskRepository.findByDiseaseId(spray.getDisease().getId()).stream()
                .sorted(Comparator.comparing((InfectionRisk r) -> 
                        Math.abs(ChronoUnit.HOURS.between(r.getAssessedAt(), spray.getApplicationDate()))))
                .limit(5)
                .collect(Collectors.toList());

        if (!risks.isEmpty()) {
            InfectionRisk closest = risks.get(0);
            
            // Effectiveness is higher if risk was elevated at time of spray
            double riskAtSpray = closest.getRiskScore();
            
            // Also reward proactive spraying (before risk spike)
            // Check if risk increased after spray
            List<InfectionRisk> afterSpray = infectionRiskRepository.findByDiseaseIdAndAssessedAtAfter(
                    spray.getDisease().getId(),
                    spray.getApplicationDate()).stream()
                    .filter(r -> r.getAssessedAt().isBefore(spray.getApplicationDate().plusHours(72)))
                    .sorted(Comparator.comparing(InfectionRisk::getRiskScore).reversed())
                    .limit(1)
                    .collect(Collectors.toList());

            double peakRiskAfterSpray = afterSpray.isEmpty() ? 0 : afterSpray.get(0).getRiskScore();
            
            // Effectiveness: high if we sprayed before risk spiked, or during high risk
            double effectiveness = Math.max(riskAtSpray, peakRiskAfterSpray * 0.8);
            effectiveness = Math.min(1.0, effectiveness);  // Cap at 1.0
            
            spray.setEfficacyAssessment(effectiveness);
            spray.setEfficacyNotes(String.format(
                    "Risk at spray: %.0f%%, Peak following 72h: %.0f%%",
                    riskAtSpray * 100, peakRiskAfterSpray * 100));
            
            log.info("Assessed spray effectiveness: {}% (risk at spray: {}%)", 
                    (int)(effectiveness * 100), (int)(riskAtSpray * 100));
            
            // Persist the efficacy assessment
            sprayApplicationRepository.save(spray);
        }
    }

    /**
     * Get recent sprays (last 7 days) to display in dashboard.
     */
    public List<SprayApplication> getRecentSprays(Long vineyardId) {
        return getSprayHistory(vineyardId, 7);
    }

    /**
     * Get spray frequency analysis - how often each fungicide is used.
     */
    public Map<String, Integer> getSprayFrequencyAnalysis(Long vineyardId, Integer lastDays) {
        List<SprayApplication> sprays = getSprayHistory(vineyardId, lastDays);
        return sprays.stream()
                .collect(Collectors.groupingBy(
                        spray -> spray.getFungicideProduct().getName(),
                        Collectors.summingInt(s -> 1)
                ));
    }
}
