package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * WBI Freiburg prognosis data for disease risk.
 * Downloaded daily from vitimeteo-bw.de PDF reports.
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
    private String riskLevel;  // "no_infection", "weak", "middle", "severe"

    private Integer riskScore;  // Numeric score if available (e.g., 50-100, 100-200, >200)

    private LocalDate incubationEndDate;  // Predicted end date of incubation period (peronospora only)

    private Integer incubationAccuracy;  // Accuracy % of incubation period prediction (peronospora only)

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt = LocalDate.now();

    private String sourceUrl;  // URL of the PDF this was extracted from

    private String rawText;  // Raw extracted text for debugging
}
