package com.rebenbot.repository;

import com.rebenbot.model.FracCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FracCodeRepository extends JpaRepository<FracCode, Long> {
    Optional<FracCode> findByCode(String code);
}
