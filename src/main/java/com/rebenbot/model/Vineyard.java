package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(name = "vineyards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Vineyard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double latitude;
    private Double longitude;

    @Column(name = "size_ares")
    private Double sizeAres;  // Size in ares (0.1 hectares = 10 ares)

    private String region;  // e.g., "Schriesheim, Baden-Württemberg"

    private String description;

    @Column(name = "last_spray_date")
    private LocalDateTime lastSprayDate;  // Track when fungicide was last applied
    
    @Column(name = "growth_stage")
    private String growthStage;  // Current growth stage (manual override or GDD-calculated)
    
    @Column(name = "is_manual_growth_stage")
    private Boolean isManualGrowthStage;  // true if manually set, false if calculated from GDD
    
    @Column(name = "growth_stage_last_updated")
    private LocalDateTime growthStageLastUpdated;  // When the growth stage was last set/calculated
    
    @Column(name = "accumulated_gdd")
    private Double accumulatedGdd;  // Accumulated Growing Degree Days since spring
    
    @Column(name = "created_at")
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
