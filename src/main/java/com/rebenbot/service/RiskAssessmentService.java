package com.rebenbot.service;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.InfectionRisk;
import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import com.rebenbot.repository.WeatherDataRepository;
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

    private final WeatherDataRepository weatherDataRepository;
    private final FungalDiseaseRepository diseaseRepository;
    private final InfectionRiskRepository infectionRiskRepository;
    private final WeatherService weatherService;

    public RiskAssessmentService(WeatherDataRepository weatherDataRepository,
                                 FungalDiseaseRepository diseaseRepository,
                                 InfectionRiskRepository infectionRiskRepository,
                                 WeatherService weatherService) {
        this.weatherDataRepository = weatherDataRepository;
        this.diseaseRepository = diseaseRepository;
        this.infectionRiskRepository = infectionRiskRepository;
        this.weatherService = weatherService;
    }

    /**
     * Assess current infection risk for all diseases based on latest weather data.
     */
    public List<InfectionRisk> assessCurrentRisk() {
        Optional<WeatherData> latestWeather = weatherService.getLatestWeatherData();

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

        log.debug("Assessed risk for {} diseases at {}", risks.size(), weather.getRecordedAt());
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
        return infectionRiskRepository.findRecentRisks(cutoff);
    }

    /**
     * Get the latest risk assessment for each disease.
     */
    public Map<String, InfectionRisk> getLatestRiskByDisease() {
        return infectionRiskRepository.findLatestRisksByAllDiseases().stream()
                .collect(Collectors.toMap(
                        r -> r.getDisease().getCommonName(),
                        r -> r,
                        (existing, replacement) -> existing  // Keep first occurrence
                ));
    }

    /**
     * Forecast risk for the next N days based on weather forecast.
     * Ensures weather data is fresh before generating forecast.
     */
    public Map<LocalDateTime, Map<String, RiskCalculator.RiskScore>> forecastRiskForDays(int days) {
        Map<LocalDateTime, Map<String, RiskCalculator.RiskScore>> forecast = new LinkedHashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime forecastEnd = now.plusDays(days);
        
        // Query for weather forecast starting from now through the next N days
        List<WeatherData> weatherForecast = weatherDataRepository.findByRecordedAtAfter(now);

        for (WeatherData weather : weatherForecast) {
            // Only include forecast data within the requested window
            if (weather.getRecordedAt().isAfter(forecastEnd)) {
                break;
            }
            
            Map<String, RiskCalculator.RiskScore> risksByDisease = new LinkedHashMap<>();

            risksByDisease.put("Peronospora", RiskCalculator.calculatePeronosporaRisk(weather));
            risksByDisease.put("Oidium", RiskCalculator.calculateOidiumRisk(weather));

            forecast.put(weather.getRecordedAt(), risksByDisease);
        }

        return forecast;
    }

}
