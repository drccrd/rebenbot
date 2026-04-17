package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Records a fungicide spray application event for validation and tracking.
 */
@Entity
@Table(name = "spray_application")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SprayApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vineyard_id", nullable = false)
    private Vineyard vineyard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fungicide_id", nullable = false)
    private FungicideProduct fungicideProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disease_id", nullable = false)
    private FungalDisease disease;

    @Column(name = "application_date", nullable = false)
    private LocalDateTime applicationDate;

    // Growth stage at time of spray (BBCH code)
    @Column(name = "growth_stage_bbch")
    private String growthStageBbch;

    // Dosage applied (liters per hectare equivalent, scaled to 10 ares)
    @Column(name = "dosage_liters_per_are")
    private Double dosageLitersPerAre;

    // Weather conditions at time of spray
    @Column(name = "temperature_c")
    private Double temperatureC;
    
    @Column(name = "humidity_percent")
    private Double humidityPercent;
    
    @Column(name = "wind_speed_msec")
    private Double windSpeedMsec;

    @Column(length = 500)
    private String notes;

    // Calculated effectiveness assessment (0-1 scale, filled after comparing to risk scores)
    @Column(name = "efficacy_assessment")
    private Double efficacyAssessment;

    @Column(name = "efficacy_notes", length = 500)
    private String efficacyNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
