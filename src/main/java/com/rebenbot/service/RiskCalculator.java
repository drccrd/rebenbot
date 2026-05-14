package com.rebenbot.service;

import com.rebenbot.model.WeatherData;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates fungal disease infection risk based on weather conditions.
 * Uses published thresholds from LVWO (Staatliche Lehr- und Versuchsanstalt für Wein- und Obstbau).
 *
 * TODO: These local threshold-based calculations are superseded by vitimeteo expert_data.json
 *       (Inkubation series), which provides per-infection-event incubation progress directly.
 *       Kept for offline/comparative use — see RiskAssessmentService for full context.
 */
@Slf4j
public class RiskCalculator {

    /**
     * Calculate Peronospora (Downy Mildew) infection risk based on weather.
     * 
     * Thresholds:
     * - Temperature: 10-25°C optimal (< 10°C or > 25°C reduces risk)
     * - Humidity: > 85% required for infection
     * - Leaf Wetness: 10+ hours at optimal temp/humidity required for sporulation
     * - Spray history: Recent fungicide application reduces risk
     * 
     * Risk scoring: 0.0 (no risk) to 1.0 (critical)
     */
    public static RiskScore calculatePeronosporaRisk(WeatherData weather) {
        return calculatePeronosporaRisk(weather, null);
    }

    /**
     * Calculate Peronospora risk with optional spray history consideration.
     * 
     * @param weather Current weather conditions
     * @param lastSprayDate Optional timestamp of last fungicide application
     * @return Risk score with spray timing factored in
     */
    public static RiskScore calculatePeronosporaRisk(WeatherData weather, java.time.LocalDateTime lastSprayDate) {
        if (weather == null) {
            return new RiskScore(0.0, "NO_DATA", "Insufficient weather data");
        }

        double tempC = weather.getTemperatureC();
        double humidity = weather.getHumidityPercent();
        double wetness = weather.getLeafWetnessIndex() != null ? weather.getLeafWetnessIndex() : 0;

        // Start with base score
        double score = 0.0;

        // Temperature factor: 10-25°C is optimal
        double tempFactor = calculateTemperatureFactor(tempC, 10.0, 25.0);
        String tempExplanation = String.format("Temp: %.1f°C (optimal 10-25°C) = %.2f factor", tempC, tempFactor);

        // Humidity factor: > 85% increases risk
        double humidityFactor = calculateHumidityFactor(humidity, 85.0);
        String humidityExplanation = String.format("Humidity: %.0f%% (risk rises above 85%%) = %.2f factor", humidity, humidityFactor);

        // Leaf wetness factor: 10+ hours increases risk significantly
        double wetnessFactor = calculateWetnessFactor(wetness, 10.0);
        String wetnessExplanation = String.format("Wetness Index: %.0f (risk rises above 0.5) = %.2f factor", wetness, wetnessFactor);

        // Combined weather score
        score = tempFactor * humidityFactor * wetnessFactor;
        String weatherCalculation = String.format("Base score: %.2f × %.2f × %.2f = %.2f", 
                tempFactor, humidityFactor, wetnessFactor, score);

        // Apply spray timing factor if available
        String sprayExplanation = "";
        if (lastSprayDate != null) {
            double sprayFactor = calculateSprayTimingFactor(lastSprayDate);
            sprayExplanation = String.format(". After spray factor (%.2f): %.2f × %.2f = %.2f", 
                    sprayFactor, score, sprayFactor, score * sprayFactor);
            score = score * sprayFactor;  // Recent spray reduces risk
        }

        // Determine risk level and recommendation
        String riskLevel;
        String recommendation;

        if (score >= 0.75) {
            riskLevel = "CRITICAL";
            recommendation = "HIGH infection risk. Spray fungicide TODAY if not already applied.";
        } else if (score >= 0.50) {
            riskLevel = "HIGH";
            recommendation = "Significant infection risk. Plan fungicide application in next 24 hours.";
        } else if (score >= 0.25) {
            riskLevel = "MEDIUM";
            recommendation = "Moderate risk. Monitor closely. Spray if conditions persist.";
        } else if (score > 0.0) {
            riskLevel = "LOW";
            recommendation = "Low infection risk. Continue monitoring weather.";
        } else {
            riskLevel = "NONE";
            recommendation = "No infection risk. Current conditions unfavorable for Peronospora.";
        }

        // Build detailed breakdown
        String breakdown = String.format("Peronospora Risk Calculation:\n%s\n%s\n%s\n%s%s\nFinal Score: %.0f%%",
                tempExplanation, humidityExplanation, wetnessExplanation, weatherCalculation, sprayExplanation, score * 100);

        return new RiskScore(score, riskLevel, recommendation, breakdown);
    }

    /**
     * Calculate Oidium (Powdery Mildew) infection risk based on weather.
     * 
     * Thresholds:
     * - Temperature: 15-27°C optimal (less dependent on exact temp than Peronospora)
     * - Humidity: 40%+ (much lower threshold than Peronospora)
     * - Leaf Wetness: Less critical (thrives in dry conditions)
     * 
     * Risk scoring: 0.0 to 1.0
     */
    public static RiskScore calculateOidiumRisk(WeatherData weather) {
        if (weather == null) {
            return new RiskScore(0.0, "NO_DATA", "Insufficient weather data");
        }

        double tempC = weather.getTemperatureC();
        double humidity = weather.getHumidityPercent();

        // Start with base score
        double score = 0.0;

        // Temperature factor: 15-27°C is optimal (broad range)
        double tempFactor = calculateTemperatureFactor(tempC, 15.0, 27.0);
        String tempExplanation = String.format("Temp: %.1f°C (optimal 15-27°C) = %.2f factor", tempC, tempFactor);

        // Humidity factor: 40%+ increases risk (less stringent than Peronospora)
        double humidityFactor = calculateHumidityFactor(humidity, 40.0);
        String humidityExplanation = String.format("Humidity: %.0f%% (risk above 40%%) = %.2f factor", humidity, humidityFactor);

        // Oidium thrives in dry conditions, so we don't penalize for low wetness
        // Actually favor drier conditions
        double drynessFactor = calculateDrynessFactor(humidity);
        String dryExplanation = String.format("Dryness factor (thrives in dry) = %.2f factor", drynessFactor);

        // Combined score
        score = tempFactor * humidityFactor * drynessFactor;
        String weatherCalculation = String.format("Base score: %.2f × %.2f × %.2f = %.2f", 
                tempFactor, humidityFactor, drynessFactor, score);

        // Determine risk level and recommendation
        String riskLevel;
        String recommendation;

        if (score >= 0.75) {
            riskLevel = "CRITICAL";
            recommendation = "CRITICAL Oidium risk. Spray immediately or consider sulfur treatment.";
        } else if (score >= 0.50) {
            riskLevel = "HIGH";
            recommendation = "High Oidium risk. Plan preventive fungicide spray today.";
        } else if (score >= 0.25) {
            riskLevel = "MEDIUM";
            recommendation = "Moderate Oidium risk. Monitor development. Spray if risk persists.";
        } else if (score > 0.0) {
            riskLevel = "LOW";
            recommendation = "Low Oidium risk. Continue monitoring.";
        } else {
            riskLevel = "NONE";
            recommendation = "No Oidium infection risk at current conditions.";
        }

        // Build detailed breakdown
        String breakdown = String.format("Oidium Risk Calculation:\n%s\n%s\n%s\n%s\nFinal Score: %.0f%%",
                tempExplanation, humidityExplanation, dryExplanation, weatherCalculation, score * 100);

        return new RiskScore(score, riskLevel, recommendation, breakdown);
    }

    /**
     * Temperature factor: 0 at extremes, 1.0 at optimal temp range.
     */
    private static double calculateTemperatureFactor(double temp, double minOptimal, double maxOptimal) {
        if (temp < minOptimal - 5 || temp > maxOptimal + 5) {
            return 0.0;  // Too cold or too hot
        }
        if (temp >= minOptimal && temp <= maxOptimal) {
            return 1.0;  // Perfect range
        }
        if (temp < minOptimal) {
            return (temp - (minOptimal - 5)) / 5.0;  // Linear interpolation
        } else {
            return ((maxOptimal + 5) - temp) / 5.0;  // Linear interpolation
        }
    }

    /**
     * Humidity factor: 0 below threshold, ramping up to 1.0 as humidity increases.
     */
    private static double calculateHumidityFactor(double humidity, double minThreshold) {
        if (humidity < minThreshold) {
            return 0.0;
        }
        if (humidity >= 95.0) {
            return 1.0;
        }
        // Linear ramp from minThreshold to 95%
        return (humidity - minThreshold) / (95.0 - minThreshold);
    }

    /**
     * Wetness factor: 0 below 10 hours, ramping to 1.0 at 16+ hours.
     */
    private static double calculateWetnessFactor(double wetnessHours, double minThreshold) {
        if (wetnessHours < minThreshold) {
            return 0.0;
        }
        if (wetnessHours >= minThreshold + 6) {
            return 1.0;
        }
        return (wetnessHours - minThreshold) / 6.0;
    }

    /**
     * Dryness factor: Oidium favors lower humidity.
     * Returns higher scores when humidity is moderate (40-60%).
     */
    private static double calculateDrynessFactor(double humidity) {
        // Optimal: 40-60% (not too wet, not too dry)
        if (humidity < 30 || humidity > 90) {
            return 0.3;  // Too dry or too wet reduces risk
        }
        if (humidity >= 40 && humidity <= 60) {
            return 1.0;  // Optimal for Oidium
        }
        if (humidity < 40) {
            return humidity / 40.0;  // Below optimal
        } else {
            return (90.0 - humidity) / 30.0;  // Above optimal
        }
    }

    /**
     * Spray timing factor: Reduces risk based on time since last fungicide application.
     * 
     * Fungicide efficacy timeline for Peronospora (German approved products):
     * - 0-3 days post-spray: 0.2 (strong protection, 80% risk reduction)
     * - 3-7 days: 0.4 (good protection, 60% risk reduction)
     * - 7-14 days: 0.7 (moderate protection, 30% risk reduction)
     * - 14+ days: 1.0 (no protection, baseline risk)
     * 
     * @param lastSprayDate Timestamp of last fungicide application
     * @return Factor (0.0-1.0) to multiply with base risk score
     */
    private static double calculateSprayTimingFactor(java.time.LocalDateTime lastSprayDate) {
        if (lastSprayDate == null) {
            return 1.0;  // No spray history, baseline risk
        }

        long hoursSinceSpray = java.time.temporal.ChronoUnit.HOURS.between(
                lastSprayDate, java.time.LocalDateTime.now());

        if (hoursSinceSpray < 0) {
            return 1.0;  // Invalid future date, use baseline
        } else if (hoursSinceSpray <= 72) {  // 0-3 days
            return 0.2;  // Strong protection
        } else if (hoursSinceSpray <= 168) {  // 3-7 days
            return 0.4;  // Good protection
        } else if (hoursSinceSpray <= 336) {  // 7-14 days
            return 0.7;  // Moderate protection
        } else {
            return 1.0;  // 14+ days, no residual protection
        }
    }

    /**
     * Represents a disease risk assessment.
     */
    public static class RiskScore {
        public final double score;  // 0.0 to 1.0
        public final String level;   // NONE, LOW, MEDIUM, HIGH, CRITICAL
        public final String recommendation;
        public final String calculationBreakdown;  // Detailed explanation of how score was calculated

        public RiskScore(double score, String level, String recommendation) {
            this(score, level, recommendation, "");
        }

        public RiskScore(double score, String level, String recommendation, String calculationBreakdown) {
            this.score = score;
            this.level = level;
            this.recommendation = recommendation;
            this.calculationBreakdown = calculationBreakdown;
        }

        @Override
        public String toString() {
            return String.format("%s (score: %.2f) - %s", level, score, recommendation);
        }
    }
}
