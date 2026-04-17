package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Represents the calculated infection risk for a fungal disease
 * at a specific point in time.
 */
@Entity
@Table(name = "risk_assessment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InfectionRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vineyard_id", nullable = false)
    private Vineyard vineyard;

    @ManyToOne
    @JoinColumn(name = "disease_id")
    private FungalDisease disease;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;

    @Column(name = "risk_score")
    private Double riskScore;  // 0.0 to 1.0

    @Column(name = "risk_level")
    private String riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL

    private String recommendation;

    @Column(name = "calculation_breakdown", length = 1000)
    private String calculationBreakdown;  // Detailed explanation of how score was calculated

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

}
