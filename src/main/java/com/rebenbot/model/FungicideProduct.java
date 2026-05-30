package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A specific fungicide product (e.g., "Dithane", "Sulfur", "Cuproxat").
 * Contains active substance and links to its FRAC code (mode of action).
 */
@Entity
@Table(name = "fungicide_product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FungicideProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "active_substance", nullable = false)
    private String activeSubstance;

    @Column(name = "base_dosage_ml_ha")
    private Double baseDosageMlHa;

    @Column(name = "phi_days")
    private Integer phiDays;  // Pre-Harvest Interval in days

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "frac_code_id", nullable = false)
    private FracCode fracCode;

    /** BVL (Bundesamt für Verbraucherschutz und Lebensmittelsicherheit) registration number. */
    @Column(name = "bvl_registration_number")
    private String bvlRegistrationNumber;

    /**
     * Whether this product has a confirmed German (BVL) product-level authorization.
     * This is distinct from EU active substance approval.
     */
    @Column(name = "bvl_approved_in_germany")
    private Boolean bvlApprovedInGermany;

    /** Date when BVL German authorization was last verified from BVL register data. */
    @Column(name = "bvl_last_verified")
    private LocalDate bvlLastVerified;

    /** Expiry date of the BVL product authorisation (Zulassungsende), from BVL PSM-API. */
    @Column(name = "bvl_approval_expiry")
    private LocalDate bvlApprovalExpiry;

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
