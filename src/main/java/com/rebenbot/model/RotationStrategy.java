package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Defines fungicide rotation strategy for a specific disease.
 * Specifies which FRAC codes should be rotated to prevent resistance development.
 * Example: For Peronospora, recommend rotating [M, U, C, 3] to prevent resistance.
 */
@Entity
@Table(name = "rotation_strategy")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotationStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "disease_id", unique = true, nullable = false)
    private FungalDisease disease;

    @Column(name = "recommended_frac_codes", nullable = false)
    private String recommendedFracCodes;  // Comma-separated FRAC codes, e.g., "M,U,C,3"

    @Column(name = "min_days_before_repeating_class")
    private Integer minDaysBeforeRepeatingClass;  // Minimum days before repeating same FRAC class

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
