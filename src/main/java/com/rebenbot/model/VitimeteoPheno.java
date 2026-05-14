package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily phenological data from vitimeteo risk_data.json (pheno_results section).
 * Covers station 99 (Freiburg area). One row per date.
 */
@Entity
@Table(name = "vitimeteo_pheno_daily")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitimeteoPheno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate phenoDate;

    private Integer bbchCode;  // BBCH phenological stage from vitimeteo model

    private Double huglinIndex;  // accumulated Huglin heliothermal index

    private Double leafCount;

    private Double leafAreaCm2;

    @Column(nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();
}
