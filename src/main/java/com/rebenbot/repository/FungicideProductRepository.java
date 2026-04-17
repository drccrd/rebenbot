package com.rebenbot.repository;

import com.rebenbot.model.FungicideProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FungicideProductRepository extends JpaRepository<FungicideProduct, Long> {
    Optional<FungicideProduct> findByName(String name);
}
