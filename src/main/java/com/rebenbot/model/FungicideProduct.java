package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @Column(name = "concentration_percent")
    private Double concentrationPercent;  // Concentration of active substance %

    @Column(name = "manufacturer_name")
    private String manufacturerName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "frac_code_id", nullable = false)
    private FracCode fracCode;

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
