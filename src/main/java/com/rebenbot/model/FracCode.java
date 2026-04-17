package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Fungicide Resistance Action Committee (FRAC) classification.
 * Groups fungicides by mode of action for resistance management.
 */
@Entity
@Table(name = "frac_code")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FracCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;  // e.g., "3", "M", "7", "11", "U", "C"

    @Column(name = "chemical_class")
    private String chemicalClass;  // e.g., "Triazole", "Multi-site contact"

    private String description;  // Detailed description of mode of action

    @Enumerated(EnumType.STRING)
    @Column(name = "resistance_risk_level")
    private ResistanceRiskLevel resistanceRiskLevel;  // LOW, MEDIUM, HIGH

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ResistanceRiskLevel {
        LOW, MEDIUM, HIGH
    }
}
