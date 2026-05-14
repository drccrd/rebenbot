package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * WBI Freiburg prognosis data for disease risk.
 * Fetched daily from vitimeteo-bw.de JSON API (risk_data.json).
 */
@Entity
@Table(name = "wbi_prognosis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WbiPrognosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Column(nullable = false)
    private String disease;  // "peronospora" or "oidium"

    @Column(nullable = false)
    private String riskLevel;  // NO_INFECTION, LOW, INFECTION_RISK, HIGH

    private String riskColor;  // raw color string from vitimeteo (e.g. "lime", "#FFAAAA")

    private Double riskScore;  // InfektionsstärkeIndex (peronospora) or OidiumIndex (oidium)

    @Column(nullable = false)
    private Boolean isForecast = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    // Peronospora-specific summary columns
    private Integer soilInfectionCount;
    private Integer infectionEventCount;
    private Integer sporulationCount;
    private Double leafWetnessHours;
    private Double leafWetnessDegreeHours;
    private Integer activeIncubationEvents;
    private LocalDate nextSprayDeadline;
    private LocalDate lastSporulationDate;

    // Oidium-specific summary columns
    private Double oidiumIndex;
    private Double ontogeneticIndex;
    private Double oidiumDailyValue;
}
