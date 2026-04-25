package com.rebenbot.repository;

import com.rebenbot.model.VineyardLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VineyardLogEntryRepository extends JpaRepository<VineyardLogEntry, Long> {
    
    List<VineyardLogEntry> findByVineyardIdOrderByEntryDateDesc(Long vineyardId);
    
    List<VineyardLogEntry> findByVineyardIdAndEntryDateBetweenOrderByEntryDateDesc(
            Long vineyardId, LocalDateTime start, LocalDateTime end);
    
    List<VineyardLogEntry> findByVineyardIdAndLogTypeOrderByEntryDateDesc(
            Long vineyardId, VineyardLogEntry.LogType logType);
    
    List<VineyardLogEntry> findByVineyardIdAndDiaryEntryTypeOrderByEntryDateDesc(
            Long vineyardId, VineyardLogEntry.DiaryEntryType diaryEntryType);
    
    List<VineyardLogEntry> findByVineyardIdAndTagsContainingOrderByEntryDateDesc(
            Long vineyardId, String tag);
    
    List<VineyardLogEntry> findByVineyardIdAndFungicideProductIdOrderByEntryDateDesc(
            Long vineyardId, Long fungicideProductId);
}
