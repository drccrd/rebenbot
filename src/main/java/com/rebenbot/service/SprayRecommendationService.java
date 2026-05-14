package com.rebenbot.service;

import com.rebenbot.model.Vineyard;
import com.rebenbot.model.VineyardLogEntry;
import com.rebenbot.model.WeatherData;
import com.rebenbot.model.WbiPrognosis;
import com.rebenbot.repository.VineyardLogEntryRepository;
import com.rebenbot.repository.VineyardRepository;
import com.rebenbot.repository.WeatherDataRepository;
import com.rebenbot.repository.WbiPrognosisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Derives the next-spray recommendation from WBI prognosis data, vineyard growth stage,
 * recent weather, and spray history.
 *
 * Peronospora (Plasmopara viticola):
 *   - Before primary infection: spray immediately when WBI shows INFECTION_RISK and no
 *     incubation data is available (primary infection scenario).
 *   - Subsequent infections: spray no later than 80% of the predicted incubation period.
 *     The 80% deadline = forecastDate + round(0.8 × (incubationEndDate − forecastDate)).
 *     Source: WBI Freiburg / Müller (1994) application of the 80% protection rule.
 *
 * Oidium (Erysiphe necator):
 *   - Only spray during the susceptibility window BBCH 53–79 (bud burst through bunch
 *     closing). Outside this window no oidium spray is required.
 *   - Spray interval is temperature-dependent (Cortesi & Pearson 1996; Gubler et al. 1987,
 *     adapted for Central European conditions by JKI/BBA):
 *       Mean temp < 10 °C   → 14 days  (pathogen nearly inactive)
 *       Mean temp 10–15 °C  → 12 days
 *       Mean temp 15–27 °C  → 10 days  (optimal growth range for E. necator)
 *       Mean temp > 27 °C   → 12 days  (sporulation reduced at high temperatures)
 *   - Critical window BBCH 65–71 (flowering to fruit set): interval reduced by 2 days.
 *
 * The overall recommendation takes the more urgent of the two disease targets.
 *
 * Urgency: URGENT (≤ 3 days), ACTION_RECOMMENDED (≤ 7), SCHEDULED (≤ 14), MONITOR (> 14).
 * actionWithin7Days = true triggers a browser notification on the frontend.
 */
@Service
@Slf4j
public class SprayRecommendationService {

    private final WbiPrognosisRepository wbiPrognosisRepository;
    private final VineyardLogEntryRepository logEntryRepository;
    private final VineyardRepository vineyardRepository;
    private final WeatherDataRepository weatherDataRepository;

    public SprayRecommendationService(WbiPrognosisRepository wbiPrognosisRepository,
                                      VineyardLogEntryRepository logEntryRepository,
                                      VineyardRepository vineyardRepository,
                                      WeatherDataRepository weatherDataRepository) {
        this.wbiPrognosisRepository = wbiPrognosisRepository;
        this.logEntryRepository = logEntryRepository;
        this.vineyardRepository = vineyardRepository;
        this.weatherDataRepository = weatherDataRepository;
    }

    public SprayRecommendation getRecommendation(Long vineyardId) {
        WbiPrognosis pero = wbiPrognosisRepository
                .findTopByDiseaseOrderByForecastDateDesc("peronospora")
                .orElse(null);
        WbiPrognosis oidium = wbiPrognosisRepository
                .findTopByDiseaseOrderByForecastDateDesc("oidium")
                .orElse(null);

        Vineyard vineyard = vineyardRepository.findById(vineyardId).orElse(null);
        int bbch = parseBbch(vineyard);
        double meanTemp = recentMeanTemperature(vineyardId);

        Optional<VineyardLogEntry> lastSprayEntry = logEntryRepository
                .findTopByVineyardIdAndLogTypeOrderByEntryDateDesc(vineyardId, VineyardLogEntry.LogType.SPRAY);

        LocalDate today = LocalDate.now();
        LocalDate lastSprayDate = lastSprayEntry.map(e -> e.getEntryDate().toLocalDate()).orElse(null);
        Long daysSinceLastSpray = lastSprayDate != null ? ChronoUnit.DAYS.between(lastSprayDate, today) : null;

        // Per-disease target dates
        LocalDate peroTarget = peronosporaTarget(pero, today);
        LocalDate oidiumTarget = oidiumTarget(bbch, meanTemp, lastSprayDate, today);

        // Combined: take the earlier (more urgent) target
        LocalDate targetDate;
        String drivingDisease;
        if (peroTarget == null && oidiumTarget == null) {
            targetDate = today.plusDays(14);
            drivingDisease = "none";
        } else if (peroTarget == null) {
            targetDate = oidiumTarget;
            drivingDisease = "oidium";
        } else if (oidiumTarget == null) {
            targetDate = peroTarget;
            drivingDisease = "peronospora";
        } else if (!peroTarget.isAfter(oidiumTarget)) {
            targetDate = peroTarget;
            drivingDisease = "peronospora";
        } else {
            targetDate = oidiumTarget;
            drivingDisease = "oidium";
        }

        if (targetDate.isBefore(today)) targetDate = today;

        long daysUntilTarget = ChronoUnit.DAYS.between(today, targetDate);
        String urgency = classifyUrgency(daysUntilTarget);
        boolean actionWithin7Days = daysUntilTarget <= 7;

        LocalDate windowStart = targetDate.minusDays(2).isBefore(today) ? today : targetDate.minusDays(2);
        LocalDate windowEnd = targetDate.plusDays(3);

        int reportedInterval = oidiumSpraysInterval(bbch, meanTemp);
        String intervalReason = buildIntervalReason(bbch, meanTemp, reportedInterval);

        return new SprayRecommendation(
                urgency,
                targetDate,
                windowStart,
                windowEnd,
                daysUntilTarget,
                actionWithin7Days,
                daysSinceLastSpray,
                lastSprayDate,
                reportedInterval,
                buildDrivingFactors(pero, oidium, bbch, meanTemp, reportedInterval,
                        daysSinceLastSpray, peroTarget, oidiumTarget, drivingDisease, intervalReason),
                buildExplanation(urgency, targetDate, daysUntilTarget, pero, oidium,
                        bbch, daysSinceLastSpray, drivingDisease),
                pero != null ? new WbiRiskSummary(pero) : null,
                oidium != null ? new WbiRiskSummary(oidium) : null
        );
    }

    // ---- Peronospora ----

    /**
     * Returns the 80%-incubation deadline for peronospora from the latest WBI forecast.
     *
     * The WBI provides forecastDate (when the incubation calculation was run) and
     * incubationEndDate (predicted sporulation date).  The 80% rule states that
     * the grower must apply a spray before 80% of the incubation period has elapsed:
     *
     *   deadline = forecastDate + round(0.8 × (incubationEndDate − forecastDate))
     *
     * Source: WBI Freiburg / Müller (1994).
     *
     * If no incubation data is available but INFECTION_RISK is active, returns today
     * (primary infection scenario — spray immediately).
     * Returns null when no active peronospora risk.
     */
    private LocalDate peronosporaTarget(WbiPrognosis pero, LocalDate today) {
        if (pero == null || !"INFECTION_RISK".equals(pero.getRiskLevel())) return null;

        // Use the precomputed 80% deadline stored from expert_data.json incubation series
        if (pero.getNextSprayDeadline() != null) {
            return pero.getNextSprayDeadline();
        }
        // No incubation deadline available — primary infection or data gap, spray immediately
        return today;
    }

    // ---- Oidium ----

    /**
     * Returns the oidium spray deadline based on the BBCH susceptibility window and
     * a temperature-adjusted interval.  Returns null when outside BBCH 53–79.
     */
    private LocalDate oidiumTarget(int bbch, double meanTemp, LocalDate lastSprayDate, LocalDate today) {
        if (bbch < 53 || bbch > 79) return null; // outside susceptibility window
        if (lastSprayDate == null) return today;  // no prior spray in window — apply now
        return lastSprayDate.plusDays(oidiumSpraysInterval(bbch, meanTemp));
    }

    /**
     * Temperature- and growth-stage-adjusted spray interval for oidium (E. necator).
     *
     * Base intervals from Cortesi & Pearson (1996) and Gubler et al. (1987), adapted
     * for Central European conditions by JKI/BBA:
     *   Mean temp < 10 °C   → 14 days  (pathogen nearly inactive)
     *   Mean temp 10–15 °C  → 12 days
     *   Mean temp 15–27 °C  → 10 days  (optimal growth range for E. necator)
     *   Mean temp > 27 °C   → 12 days  (sporulation reduced at high temperatures)
     *
     * Critical window adjustment: during BBCH 65–71 (flowering to fruit set) the
     * interval is reduced by 2 days (minimum 7 days).
     */
    private int oidiumSpraysInterval(int bbch, double meanTemp) {
        int interval;
        if (meanTemp < 10.0)       interval = 14;
        else if (meanTemp < 15.0)  interval = 12;
        else if (meanTemp <= 27.0) interval = 10;
        else                       interval = 12;
        if (bbch >= 65 && bbch <= 71) interval = Math.max(7, interval - 2);
        return interval;
    }

    // ---- Helpers ----

    private int parseBbch(Vineyard vineyard) {
        if (vineyard == null || vineyard.getGrowthStage() == null) return 0;
        // Stage is stored as a key like "FLOWERING" or "FOUR_LEAVES" — use the canonical BBCH map.
        Integer mapped = GrowthStageService.STAGE_TO_BBCH.get(vineyard.getGrowthStage());
        if (mapped != null) return mapped;
        // Fallback: extract leading digits in case the field ever stores a raw BBCH code
        try {
            String digits = vineyard.getGrowthStage().replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Mean temperature (°C) from recent weather records for this vineyard (last 7 days).
     * Falls back to 15 °C (neutral within the 10-day-interval band) when no data.
     */
    private double recentMeanTemperature(Long vineyardId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<WeatherData> recent = weatherDataRepository.findTop14ByVineyardIdOrderByRecordedAtDesc(vineyardId);
        return recent.stream()
                .filter(w -> w.getRecordedAt() != null && w.getRecordedAt().isAfter(cutoff))
                .filter(w -> w.getTemperatureC() != null)
                .mapToDouble(WeatherData::getTemperatureC)
                .average()
                .orElse(15.0);
    }

    private String classifyUrgency(long daysUntil) {
        if (daysUntil <= 3)       return "URGENT";
        if (daysUntil <= 7)       return "ACTION_RECOMMENDED";
        if (daysUntil <= 14)      return "SCHEDULED";
        return "MONITOR";
    }

    private String buildIntervalReason(int bbch, double meanTemp, int interval) {
        if (bbch < 53 || bbch > 79) {
            return "Oidium: outside susceptibility window (BBCH " + bbch + ")";
        }
        String tempBand;
        if (meanTemp < 10.0)       tempBand = "<10 °C, low risk";
        else if (meanTemp < 15.0)  tempBand = "10–15 °C";
        else if (meanTemp <= 27.0) tempBand = "15–27 °C, optimal growth";
        else                       tempBand = ">27 °C, reduced sporulation";
        String criticalNote = (bbch >= 65 && bbch <= 71) ? " −2d critical window (BBCH 65–71)" : "";
        return String.format("Oidium %dd (mean temp %.1f °C, %s)%s", interval, meanTemp, tempBand, criticalNote);
    }

    private Map<String, String> buildDrivingFactors(WbiPrognosis pero, WbiPrognosis oidium,
                                                    int bbch, double meanTemp, int oidiumInterval,
                                                    Long daysSinceLastSpray,
                                                    LocalDate peroTarget, LocalDate oidiumTarget,
                                                    String drivingDisease, String intervalReason) {
        Map<String, String> factors = new LinkedHashMap<>();

        if (pero != null) {
            String s = String.format("%s (%.1f%%)", pero.getRiskLevel(),
                    pero.getRiskScore() != null ? pero.getRiskScore() : 0.0);
            if (pero.getNextSprayDeadline() != null) {
                s += " — spray by " + pero.getNextSprayDeadline();
            }
            if (peroTarget != null) s += " → 80% deadline: " + peroTarget;
            factors.put("peronospora_wbi", s);
        } else {
            factors.put("peronospora_wbi", "No WBI data available");
        }

        if (bbch > 0) {
            if (bbch < 53 || bbch > 79) {
                factors.put("oidium_status",
                        "Outside susceptibility window — BBCH " + bbch + " (" + bbchLabel(bbch) + ")");
            } else {
                String s = "Active risk window — BBCH " + bbch + " (" + bbchLabel(bbch) + ")";
                s += "; mean temp " + String.format("%.1f", meanTemp) + " °C → " + oidiumInterval + "d interval";
                if (oidiumTarget != null) s += " → due " + oidiumTarget;
                factors.put("oidium_status", s);
            }
        } else {
            factors.put("oidium_status", "Growth stage unknown");
        }

        if (oidium != null) {
            factors.put("oidium_wbi", String.format("%s (%.1f%%)", oidium.getRiskLevel(),
                    oidium.getRiskScore() != null ? oidium.getRiskScore() : 0.0));
        } else {
            factors.put("oidium_wbi", "No WBI data available");
        }

        factors.put("days_since_last_spray",
                daysSinceLastSpray != null ? daysSinceLastSpray + " days" : "No spray recorded yet");
        factors.put("driving_disease", drivingDisease);
        factors.put("interval_reason", intervalReason);

        return factors;
    }

    private String buildExplanation(String urgency, LocalDate targetDate, long daysUntil,
                                    WbiPrognosis pero, WbiPrognosis oidium,
                                    int bbch, Long daysSince, String drivingDisease) {
        String riskSummary = buildRiskSummary(pero, oidium, bbch);
        return switch (urgency) {
            case "URGENT" ->
                    String.format("Spray now — driven by %s. %s", drivingDisease, riskSummary);
            case "ACTION_RECOMMENDED" ->
                    String.format("Spray within %d day(s) — target %s (driven by %s). %s",
                            daysUntil, targetDate, drivingDisease, riskSummary);
            case "SCHEDULED" ->
                    String.format("Next spray due %s (%d days, driven by %s). %s",
                            targetDate, daysUntil, drivingDisease, riskSummary);
            default ->
                    String.format("Monitor conditions. %s", riskSummary);
        };
    }

    private String buildRiskSummary(WbiPrognosis pero, WbiPrognosis oidium, int bbch) {
        List<String> parts = new ArrayList<>();
        if (pero != null && "INFECTION_RISK".equals(pero.getRiskLevel()))
            parts.add("Peronospora " + pero.getRiskScore() + "% infection risk");
        if (oidium != null && "INFECTION_RISK".equals(oidium.getRiskLevel()) && bbch >= 53 && bbch <= 79)
            parts.add("Oidium " + oidium.getRiskScore() + "% infection risk (active window)");
        return parts.isEmpty() ? "No active infection risk detected by WBI." : String.join("; ", parts) + ".";
    }

    private String bbchLabel(int bbch) {
        if (bbch < 53)             return "pre-susceptibility";
        if (bbch <= 59)            return "bud burst / pre-bloom";
        if (bbch <= 64)            return "pre-bloom";
        if (bbch <= 68)            return "flowering";
        if (bbch <= 71)            return "fruit set";
        if (bbch <= 75)            return "berry development";
        if (bbch <= 79)            return "bunch closing";
        return "post-bunch closing";
    }

    // ---- Response DTOs ----

    public static class SprayRecommendation {
        public final String urgency;
        public final LocalDate targetDate;
        public final LocalDate windowStart;
        public final LocalDate windowEnd;
        public final long daysUntilTarget;
        public final boolean actionWithin7Days;
        public final Long daysSinceLastSpray;
        public final LocalDate lastSprayDate;
        public final int recommendedIntervalDays;
        public final Map<String, String> drivingFactors;
        public final String explanation;
        public final WbiRiskSummary wbiPeronospora;
        public final WbiRiskSummary wbiOidium;

        public SprayRecommendation(String urgency, LocalDate targetDate, LocalDate windowStart,
                                   LocalDate windowEnd, long daysUntilTarget, boolean actionWithin7Days,
                                   Long daysSinceLastSpray, LocalDate lastSprayDate, int recommendedIntervalDays,
                                   Map<String, String> drivingFactors, String explanation,
                                   WbiRiskSummary wbiPeronospora, WbiRiskSummary wbiOidium) {
            this.urgency = urgency;
            this.targetDate = targetDate;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.daysUntilTarget = daysUntilTarget;
            this.actionWithin7Days = actionWithin7Days;
            this.daysSinceLastSpray = daysSinceLastSpray;
            this.lastSprayDate = lastSprayDate;
            this.recommendedIntervalDays = recommendedIntervalDays;
            this.drivingFactors = drivingFactors;
            this.explanation = explanation;
            this.wbiPeronospora = wbiPeronospora;
            this.wbiOidium = wbiOidium;
        }
    }

    public static class WbiRiskSummary {
        public final String disease;
        public final String riskLevel;
        public final Double riskScore;
        public final LocalDate nextSprayDeadline;
        public final LocalDate forecastDate;

        public WbiRiskSummary(WbiPrognosis prognosis) {
            this.disease = prognosis.getDisease();
            this.riskLevel = prognosis.getRiskLevel();
            this.riskScore = prognosis.getRiskScore();
            this.nextSprayDeadline = prognosis.getNextSprayDeadline();
            this.forecastDate = prognosis.getForecastDate();
        }
    }
}
