package com.rebenbot.service;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.InfectionRisk;
import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import com.rebenbot.repository.WeatherDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to calculate and manage infection risk assessments.
 * Combines weather data with disease thresholds to generate risk scores.
 */
@Service
@Slf4j
public class RiskAssessmentService {

    @Autowired
    private WeatherDataRepository weatherDataRepository;

    @Autowired
    private FungalDiseaseRepository diseaseRepository;

    @Autowired
    private InfectionRiskRepository infectionRiskRepository;

    /**
     * Assess current infection risk for all diseases based on latest weather data.
     */
    public List<InfectionRisk> assessCurrentRisk() {
        Optional<WeatherData> latestWeather = weatherDataRepository.findAll().stream()
                .max(Comparator.comparing(WeatherData::getRecordedAt));

        if (latestWeather.isEmpty()) {
            log.warn("No weather data available for risk assessment");
            return Collections.emptyList();
        }

        return assessRiskForWeather(latestWeather.get());
    }

    /**
     * Calculate risk for all diseases given weather conditions.
     */
    public List<InfectionRisk> assessRiskForWeather(WeatherData weather) {
        List<InfectionRisk> risks = new ArrayList<>();

        // Get all diseases
        List<FungalDisease> diseases = diseaseRepository.findAll();

        for (FungalDisease disease : diseases) {
            InfectionRisk risk = calculateRiskForDisease(disease, weather);
            risks.add(infectionRiskRepository.save(risk));
        }

        log.info("Assessed risk for {} diseases at {}", risks.size(), weather.getRecordedAt());
        return risks;
    }

    /**
     * Calculate infection risk for a specific disease at a specific time.
     */
    public InfectionRisk calculateRiskForDisease(FungalDisease disease, WeatherData weather) {
        RiskCalculator.RiskScore riskScore;

        if ("Peronospora".equalsIgnoreCase(disease.getCommonName())) {
            riskScore = RiskCalculator.calculatePeronosporaRisk(weather);
        } else if ("Oidium".equalsIgnoreCase(disease.getCommonName())) {
            riskScore = RiskCalculator.calculateOidiumRisk(weather);
        } else {
            // Default: no risk for unknown diseases
            riskScore = new RiskCalculator.RiskScore(0.0, "UNKNOWN", "No risk model available");
        }

        return InfectionRisk.builder()
                .vineyard(weather.getVineyard())
                .disease(disease)
                .assessedAt(LocalDateTime.now())
                .riskScore(riskScore.score)
                .riskLevel(riskScore.level)
                .recommendation(riskScore.recommendation)
                .calculationBreakdown(riskScore.calculationBreakdown)
                .build();
    }

    /**
     * Get risk assessment for the last N hours.
     */
    public List<InfectionRisk> getRiskHistory(int hoursBack) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursBack);
        return infectionRiskRepository.findAll().stream()
                .filter(r -> r.getAssessedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(InfectionRisk::getAssessedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get the latest risk assessment for each disease.
     */
    public Map<String, InfectionRisk> getLatestRiskByDisease() {
        return infectionRiskRepository.findAll().stream()
                .sorted(Comparator.comparing(InfectionRisk::getAssessedAt).reversed())
                .collect(Collectors.toMap(
                        r -> r.getDisease().getCommonName(),
                        r -> r,
                        (existing, replacement) -> existing  // Keep first occurrence
                ));
    }

    /**
     * Forecast risk for the next 7 days based on weather forecast.
     */
    public Map<LocalDateTime, Map<String, RiskCalculator.RiskScore>> forecastRiskForDays(int days) {
        Map<LocalDateTime, Map<String, RiskCalculator.RiskScore>> forecast = new LinkedHashMap<>();

        // Get all weather data for the next N days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<WeatherData> weatherForecast = weatherDataRepository.findAll().stream()
                .filter(w -> w.getRecordedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(WeatherData::getRecordedAt))
                .collect(Collectors.toList());

        for (WeatherData weather : weatherForecast) {
            Map<String, RiskCalculator.RiskScore> risksByDisease = new LinkedHashMap<>();

            risksByDisease.put("Peronospora", RiskCalculator.calculatePeronosporaRisk(weather));
            risksByDisease.put("Oidium", RiskCalculator.calculateOidiumRisk(weather));

            forecast.put(weather.getRecordedAt(), risksByDisease);
        }

        return forecast;
    }

}
