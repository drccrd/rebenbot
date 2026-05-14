package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One peronospora infection event as tracked by the vitimeteo expert_data.json API.
 * Each event corresponds to one Inkubation series (e.g. "series_7016_5").
 * Incubation progresses from 0% to 100%; reaching 100% marks sporulation onset.
 */
@Entity
@Table(name = "peronospora_infection_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeronosporaInfectionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String seriesId;  // e.g. "series_7016_5" — stable identifier from vitimeteo

    @Column(nullable = false)
    private LocalDateTime infectionDatetime;  // first timestamp in the Inkubation series

    private Double incubationPctLatest;  // most recent incubation % from the series

    private LocalDateTime incubation80PctDatetime;  // estimated datetime incubation reaches 80% (spray deadline)

    private LocalDateTime sporulationDatetime;  // datetime incubation reached/will reach 100%

    @Column(nullable = false)
    private Boolean isActive = true;  // false once sporulation is confirmed complete

    @Column(nullable = false)
    private LocalDate fetchedDate;  // date this row was last refreshed from the API
}
