package com.rebenbot.repository;

import com.rebenbot.model.Vineyard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VineyardRepository extends JpaRepository<Vineyard, Long> {
}
