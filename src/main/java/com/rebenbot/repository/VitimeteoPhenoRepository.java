package com.rebenbot.repository;

import com.rebenbot.model.VitimeteoPheno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VitimeteoPhenoRepository extends JpaRepository<VitimeteoPheno, Long> {

    Optional<VitimeteoPheno> findByPhenoDate(LocalDate phenoDate);

    List<VitimeteoPheno> findByPhenoDateBetweenOrderByPhenoDateDesc(LocalDate from, LocalDate to);

    Optional<VitimeteoPheno> findTopByOrderByPhenoDateDesc();
}
