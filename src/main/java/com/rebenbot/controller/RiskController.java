package com.rebenbot.controller;

import com.rebenbot.model.InfectionRisk;
import com.rebenbot.service.RiskAssessmentService;
import com.rebenbot.service.RiskCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/v1/risk")
@CrossOrigin(origins = "*")
public class RiskController {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    /**
     * Calculate current infection risk based on latest weather data.
     */
    @PostMapping("/assess")
    public ResponseEntity<?> assessCurrentRisk() {
        List<InfectionRisk> risks = riskAssessmentService.assessCurrentRisk();

        if (risks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "No weather data available. Fetch weather data first: POST /api/v1/weather/fetch"
            ));
        }

        List<Map<String, Object>> risksList = new ArrayList<>();
        for (InfectionRisk r : risks) {
            risksList.add(Map.of(
                    "disease", r.getDisease().getCommonName(),
                    "riskLevel", r.getRiskLevel(),
                    "riskScore", r.getRiskScore(),
                    "recommendation", r.getRecommendation(),
                    "calculationBreakdown", r.getCalculationBreakdown() != null ? r.getCalculationBreakdown() : "",
                    "assessedAt", r.getAssessedAt().toString()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "riskAssessmentsCount", risks.size(),
                "risks", risksList
        ));
    }

    /**
     * Get latest risk for each disease.
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestRiskByDisease() {
        Map<String, InfectionRisk> latest = riskAssessmentService.getLatestRiskByDisease();

        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        for (Map.Entry<String, InfectionRisk> entry : latest.entrySet()) {
            InfectionRisk risk = entry.getValue();
            response.put(entry.getKey(), Map.of(
                    "riskLevel", risk.getRiskLevel(),
                    "riskScore", risk.getRiskScore(),
                    "recommendation", risk.getRecommendation(),
                    "calculationBreakdown", risk.getCalculationBreakdown() != null ? risk.getCalculationBreakdown() : "",
                    "assessedAt", risk.getAssessedAt().toString()
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get risk history for last N hours.
     */
    @GetMapping("/history")
    public ResponseEntity<?> getRiskHistory(
            @RequestParam(value = "hours", defaultValue = "48") int hoursBack) {

        List<InfectionRisk> history = riskAssessmentService.getRiskHistory(hoursBack);

        List<Map<String, Object>> records = new ArrayList<>();
        for (InfectionRisk r : history) {
            records.add(Map.of(
                    "disease", r.getDisease().getCommonName(),
                    "riskLevel", r.getRiskLevel(),
                    "riskScore", r.getRiskScore(),
                    "assessedAt", r.getAssessedAt().toString()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "period", hoursBack + " hours",
                "recordsCount", history.size(),
                "records", records
        ));
    }

    /**
     * Forecast risk for the next N days based on weather forecast.
     */
    @GetMapping("/forecast")
    public ResponseEntity<?> forecastRisk(
            @RequestParam(value = "days", defaultValue = "7") int days) {

        Map<LocalDateTime, Map<String, RiskCalculator.RiskScore>> forecast =
                riskAssessmentService.forecastRiskForDays(days);

        if (forecast.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "No weather forecast available"
            ));
        }

        List<Map<String, Object>> forecastData = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, RiskCalculator.RiskScore>> entry : forecast.entrySet()) {
            Map<String, Object> diseaseRisks = new LinkedHashMap<>();
            for (Map.Entry<String, RiskCalculator.RiskScore> diseaseEntry : entry.getValue().entrySet()) {
                RiskCalculator.RiskScore score = diseaseEntry.getValue();
                diseaseRisks.put(diseaseEntry.getKey(), Map.of(
                        "score", score.score,
                        "level", score.level,
                        "recommendation", score.recommendation
                ));
            }
            forecastData.add(Map.of(
                    "forecastTime", entry.getKey().toString(),
                    "diseases", diseaseRisks
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "forecastDays", days,
                "recordsCount", forecastData.size(),
                "forecast", forecastData
        ));
    }

}
