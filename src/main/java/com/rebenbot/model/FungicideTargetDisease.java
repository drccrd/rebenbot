package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Links a fungicide product to a target disease.
 * Includes recommended dosage and efficacy rating for that specific pairing.
 */
@Entity
@Table(name = "fungicide_target_disease", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "disease_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FungicideTargetDisease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private FungicideProduct product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "disease_id", nullable = false)
    private FungalDisease disease;

    @Column(name = "recommended_dosage_ml_per_100l", nullable = false)
    private Double recommendedDosageMlPer100l;

    @Column(name = "efficacy_rating")
    private Integer efficacyRating;  // 0-5 scale, where 5 is most effective

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
}
