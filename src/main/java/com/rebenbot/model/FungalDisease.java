package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a fungal disease that affects vineyards.
 * Primary diseases of concern: Peronospora and Oidium
 */
@Entity
@Table(name = "fungal_diseases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FungalDisease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_name")
    private String commonName;  // e.g., "Peronospora", "Oidium"

    @Column(name = "scientific_name")
    private String scientificName;

    @Column(name = "german_name")
    private String germanName;

    @Column(name = "temp_min_c")
    private Double tempMinC;  // Minimum temperature for infection
    
    @Column(name = "temp_max_c")
    private Double tempMaxC;  // Maximum temperature for infection

    @Column(name = "humidity_min_percent")
    private Double humidityMinPercent;  // Minimum humidity for infection

    private String description;

}
