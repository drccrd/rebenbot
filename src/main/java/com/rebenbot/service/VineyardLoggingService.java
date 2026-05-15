package com.rebenbot.service;

import com.rebenbot.model.*;
import com.rebenbot.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified service for vineyard logging - handles both spray applications and general diary entries.
 */
@Service
@Slf4j
public class VineyardLoggingService {

    private final VineyardLogEntryRepository logRepository;
    private final VineyardRepository vineyardRepository;
    private final FungicideProductRepository fungicideProductRepository;
    private final FungalDiseaseRepository diseaseRepository;
    private final InfectionRiskRepository infectionRiskRepository;

    public VineyardLoggingService(
            VineyardLogEntryRepository logRepository,
            VineyardRepository vineyardRepository,
            FungicideProductRepository fungicideProductRepository,
            FungalDiseaseRepository diseaseRepository,
            InfectionRiskRepository infectionRiskRepository) {
        this.logRepository = logRepository;
        this.vineyardRepository = vineyardRepository;
        this.fungicideProductRepository = fungicideProductRepository;
        this.diseaseRepository = diseaseRepository;
        this.infectionRiskRepository = infectionRiskRepository;
    }

    /**
     * Calculate fungicide dosage based on product specifications and vineyard size.
     */
    private double calculateDosage(FungicideProduct fungicideProduct, double vineyardSizeAres, String growthStageBbch) {
        Double baseDosageMlHa = fungicideProduct.getBaseDosageMlHa();
        if (baseDosageMlHa == null || baseDosageMlHa <= 0) {
            log.warn("No baseDosageMlHa set for fungicide '{}', defaulting to 0", fungicideProduct.getName());
            return 0.0;
        }
        // Convert: mL/ha * (ares / 100) = mL, then / 1000 = L
        double dosageLiters = baseDosageMlHa * (vineyardSizeAres / 100.0) / 1000.0;

        if (growthStageBbch != null && (growthStageBbch.startsWith("80") || growthStageBbch.startsWith("81"))) {
            dosageLiters *= 0.8;
        }

        log.debug("Calculated dosage: {}L for {} ares, fungicide: {}, growth stage: {}",
                String.format("%.2f", dosageLiters), vineyardSizeAres, fungicideProduct.getName(), growthStageBbch);

        return dosageLiters;
    }

    // ==================== SPRAY APPLICATION METHODS ====================

    /**
     * Record a spray application.
     */
    public VineyardLogEntry recordSpray(Long vineyardId, Long fungicideId, Long diseaseId,
                                       LocalDateTime applicationDate, String growthStageBbch,
                                       Double temperatureC, Double humidityPercent, Double windSpeedMsec,
                                       Double amountFungicideAppliedLiters, String notes) {
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found: " + vineyardId));
        
        FungicideProduct fungicideProduct = fungicideProductRepository.findById(fungicideId)
                .orElseThrow(() -> new RuntimeException("Fungicide not found: " + fungicideId));
        
        FungalDisease disease = diseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new RuntimeException("Disease not found: " + diseaseId));

        double dosageLitersPerAre = calculateDosage(fungicideProduct, vineyard.getSizeAres(), growthStageBbch);

        VineyardLogEntry logEntry = VineyardLogEntry.builder()
                .vineyard(vineyard)
                .logType(VineyardLogEntry.LogType.SPRAY)
                .entryDate(applicationDate)
                .fungicideProduct(fungicideProduct)
                .disease(disease)
                .dosageLitersPerAre(dosageLitersPerAre)
                .amountFungicideAppliedLiters(amountFungicideAppliedLiters)
                .growthStageBbch(growthStageBbch)
                .temperatureC(temperatureC)
                .humidityPercent(humidityPercent)
                .windSpeedMsec(windSpeedMsec)
                .description(notes)
                .build();

        VineyardLogEntry saved = logRepository.save(logEntry);
        log.info("Recorded spray: {} on vineyard {} for {} using {} - Amount applied: {} liters", 
                applicationDate, vineyardId, disease.getCommonName(), fungicideProduct.getName(), amountFungicideAppliedLiters);

        return saved;
    }

    /**
     * Get spray history for a vineyard.
     */
    public List<VineyardLogEntry> getSprayHistory(Long vineyardId, Integer lastDays) {
        if (lastDays != null && lastDays > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(lastDays);
            return logRepository.findByVineyardIdAndEntryDateBetweenOrderByEntryDateDesc(vineyardId, cutoff, LocalDateTime.now())
                    .stream()
                    .filter(e -> e.getLogType() == VineyardLogEntry.LogType.SPRAY)
                    .collect(Collectors.toList());
        }
        return logRepository.findByVineyardIdAndLogTypeOrderByEntryDateDesc(vineyardId, VineyardLogEntry.LogType.SPRAY);
    }

    /**
     * Get recent sprays (last 7 days).
     */
    public List<VineyardLogEntry> getRecentSprays(Long vineyardId) {
        return getSprayHistory(vineyardId, 7);
    }

    /**
     * Get spray frequency analysis.
     */
    public Map<String, Integer> getSprayFrequencyAnalysis(Long vineyardId, Integer lastDays) {
        List<VineyardLogEntry> sprays = getSprayHistory(vineyardId, lastDays);
        return sprays.stream()
                .collect(Collectors.groupingBy(
                        spray -> spray.getFungicideProduct().getName(),
                        Collectors.summingInt(s -> 1)
                ));
    }

    /**
     * Assess spray effectiveness.
     */
    public void assessSprayEffectiveness(VineyardLogEntry logEntry) {
        if (logEntry.getLogType() != VineyardLogEntry.LogType.SPRAY) {
            return;
        }

        List<InfectionRisk> risks = infectionRiskRepository.findByDiseaseId(logEntry.getDisease().getId()).stream()
                .sorted(Comparator.comparing((InfectionRisk r) -> 
                        Math.abs(ChronoUnit.HOURS.between(r.getAssessedAt(), logEntry.getEntryDate()))))
                .limit(5)
                .collect(Collectors.toList());

        if (!risks.isEmpty()) {
            InfectionRisk closest = risks.get(0);
            double riskAtSpray = closest.getRiskScore();
            
            List<InfectionRisk> afterSpray = infectionRiskRepository.findByDiseaseIdAndAssessedAtAfter(
                    logEntry.getDisease().getId(),
                    logEntry.getEntryDate()).stream()
                    .filter(r -> r.getAssessedAt().isBefore(logEntry.getEntryDate().plusHours(72)))
                    .sorted(Comparator.comparing(InfectionRisk::getRiskScore).reversed())
                    .limit(1)
                    .collect(Collectors.toList());

            double peakRiskAfterSpray = afterSpray.isEmpty() ? 0 : afterSpray.get(0).getRiskScore();
            
            double effectiveness = Math.max(riskAtSpray, peakRiskAfterSpray * 0.8);
            effectiveness = Math.min(1.0, effectiveness);
            
            logEntry.setEfficacyAssessment(effectiveness);
            logEntry.setEfficacyNotes(String.format(
                    "Risk at spray: %.0f%%, Peak following 72h: %.0f%%",
                    riskAtSpray * 100, peakRiskAfterSpray * 100));
            
            log.debug("Assessed spray effectiveness: {}% (risk at spray: {}%)", 
                    (int)(effectiveness * 100), (int)(riskAtSpray * 100));
            
            logRepository.save(logEntry);
        }
    }

    // ==================== DIARY ENTRY METHODS ====================

    /**
     * Create a general diary entry.
     */
    public VineyardLogEntry createDiaryEntry(Long vineyardId, LocalDateTime entryDate, String title,
                                             String description, VineyardLogEntry.DiaryEntryType diaryEntryType,
                                             String growthStageBbch, String tags) {
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found: " + vineyardId));

        VineyardLogEntry logEntry = VineyardLogEntry.builder()
                .vineyard(vineyard)
                .logType(VineyardLogEntry.LogType.valueOf(diaryEntryType.name()))
                .entryDate(entryDate)
                .title(title)
                .description(description)
                .diaryEntryType(diaryEntryType)
                .growthStageBbch(growthStageBbch)
                .tags(tags)
                .build();

        VineyardLogEntry saved = logRepository.save(logEntry);
        log.info("Created diary entry: '{}' for vineyard {} on {}", title, vineyardId, entryDate);

        return saved;
    }

    /**
     * Get all log entries for a vineyard.
     */
    public List<VineyardLogEntry> getLogEntries(Long vineyardId) {
        return logRepository.findByVineyardIdOrderByEntryDateDesc(vineyardId);
    }

    /**
     * Get all diary entries for a vineyard (excluding sprays).
     */
    public List<VineyardLogEntry> getDiaryEntries(Long vineyardId) {
        return getLogEntries(vineyardId).stream()
                .filter(e -> e.getLogType() != VineyardLogEntry.LogType.SPRAY)
                .collect(Collectors.toList());
    }

    /**
     * Get diary entries within a date range.
     */
    public List<VineyardLogEntry> getDiaryEntriesByDateRange(Long vineyardId, LocalDateTime start, LocalDateTime end) {
        return logRepository.findByVineyardIdAndEntryDateBetweenOrderByEntryDateDesc(vineyardId, start, end)
                .stream()
                .filter(e -> e.getLogType() != VineyardLogEntry.LogType.SPRAY)
                .collect(Collectors.toList());
    }

    /**
     * Get diary entries of a specific type.
     */
    public List<VineyardLogEntry> getDiaryEntriesByType(Long vineyardId, VineyardLogEntry.DiaryEntryType entryType) {
        return logRepository.findByVineyardIdAndDiaryEntryTypeOrderByEntryDateDesc(vineyardId, entryType);
    }

    /**
     * Get diary entries containing a specific tag.
     */
    public List<VineyardLogEntry> getDiaryEntriesByTag(Long vineyardId, String tag) {
        return logRepository.findByVineyardIdAndTagsContainingOrderByEntryDateDesc(vineyardId, tag);
    }

    /**
     * Update a log entry.
     */
    public VineyardLogEntry updateLogEntry(Long entryId, String title, String description,
                                           VineyardLogEntry.DiaryEntryType diaryEntryType,
                                           String growthStageBbch, String tags) {
        VineyardLogEntry logEntry = logRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Log entry not found: " + entryId));

        logEntry.setTitle(title);
        logEntry.setDescription(description);
        logEntry.setDiaryEntryType(diaryEntryType);
        logEntry.setGrowthStageBbch(growthStageBbch);
        logEntry.setTags(tags);

        VineyardLogEntry updated = logRepository.save(logEntry);
        log.info("Updated log entry: {}", entryId);

        return updated;
    }

    /**
     * Delete a log entry.
     */
    public void deleteLogEntry(Long entryId) {
        logRepository.deleteById(entryId);
        log.info("Deleted log entry: {}", entryId);
    }

    /**
     * Get a specific log entry by ID.
     */
    public VineyardLogEntry getLogEntry(Long entryId) {
        return logRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Log entry not found: " + entryId));
    }
}
