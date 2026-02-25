package com.zuqi.repository;

import com.zuqi.domain.gl.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    List<JournalEntryLine> findByJournalEntryIdOrderByLineNumberAsc(UUID journalEntryId);

    @Query("""
        SELECT jel FROM JournalEntryLine jel
        JOIN jel.journalEntry je
        WHERE je.distributorId = :distributorId
          AND je.periodId = :periodId
          AND je.status = 'POSTED'
        """)
    List<JournalEntryLine> findPostedLinesForPeriod(
            @Param("distributorId") UUID distributorId,
            @Param("periodId") UUID periodId);
}
