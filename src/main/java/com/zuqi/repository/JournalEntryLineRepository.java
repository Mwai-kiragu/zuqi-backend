package com.zuqi.repository;

import com.zuqi.domain.gl.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    @Query("""
        SELECT jel FROM JournalEntryLine jel
        JOIN FETCH jel.journalEntry je
        JOIN FETCH jel.account
        WHERE je.distributorId = :distributorId
          AND je.entryDate >= :fromDate
          AND je.entryDate <= :toDate
          AND je.status = 'POSTED'
        ORDER BY je.entryDate ASC, je.entryNumber ASC, jel.lineNumber ASC
        """)
    List<JournalEntryLine> findPostedLinesForDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("""
        SELECT jel FROM JournalEntryLine jel
        JOIN FETCH jel.journalEntry je
        JOIN FETCH jel.account
        WHERE je.distributorId = :distributorId
          AND je.entryDate <= :toDate
          AND je.status = 'POSTED'
        """)
    List<JournalEntryLine> findPostedLinesUpToDate(
            @Param("distributorId") UUID distributorId,
            @Param("toDate") LocalDate toDate);

    @Query("""
        SELECT jel FROM JournalEntryLine jel
        JOIN FETCH jel.journalEntry je
        JOIN FETCH jel.account
        WHERE je.distributorId = :distributorId
          AND je.entryDate < :fromDate
          AND je.status = 'POSTED'
        """)
    List<JournalEntryLine> findPostedLinesBeforeDate(
            @Param("distributorId") UUID distributorId,
            @Param("fromDate") LocalDate fromDate);
}
