package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Regional approval information for a fungicide product.
 * Tracks approval status, validity dates, PHI, and max dosages.
 */
@Entity
@Table(name = "fungicide_approval")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FungicideApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private FungicideProduct product;

    @Column(nullable = false)
    private String region;  // e.g., "Germany", "EU"

    @Column(name = "approval_valid_from")
    private LocalDate approvalValidFrom;

    @Column(name = "approval_valid_until")
    private LocalDate approvalValidUntil;

    @Column(name = "phi_days_before_harvest")
    private Integer phiDaysBeforeHarvest;  // Pre-Harvest Interval in days

    @Column(name = "max_dosage_ml_per_100l")
    private Double maxDosageMlPer100l;  // Maximum dosage per application

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus;  // ACTIVE, PENDING, EXPIRED
    // IMPORTANT: Enum values MUST match DB CHECK constraint in migration.
    // If enum values change, update V1__initial_schema.sql CHECK constraint:
    // CHECK (approval_status IN ('ACTIVE', 'PENDING', 'EXPIRED'))

    private String notes;

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

    public enum ApprovalStatus {
        ACTIVE, PENDING, EXPIRED
    }
}
