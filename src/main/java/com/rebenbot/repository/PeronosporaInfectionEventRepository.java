package com.rebenbot.repository;

import com.rebenbot.model.PeronosporaInfectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PeronosporaInfectionEventRepository extends JpaRepository<PeronosporaInfectionEvent, Long> {

    Optional<PeronosporaInfectionEvent> findBySeriesId(String seriesId);

    List<PeronosporaInfectionEvent> findByIsActiveTrueOrderByInfectionDatetimeDesc();

    List<PeronosporaInfectionEvent> findByFetchedDateOrderByInfectionDatetimeDesc(LocalDate fetchedDate);
}
