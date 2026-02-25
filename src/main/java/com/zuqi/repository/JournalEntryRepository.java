package com.zuqi.repository;

import com.zuqi.domain.gl.JournalEntry;
import com.zuqi.domain.gl.JournalEntryStatus;
import com.zuqi.domain.gl.JournalSourceModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>,
        JpaSpecificationExecutor<JournalEntry> {

    Page<JournalEntry> findByDistributorIdOrderByEntryDateDescCreatedAtDesc(UUID distributorId, Pageable pageable);

    List<JournalEntry> findByDistributorIdAndStatus(UUID distributorId, JournalEntryStatus status);

    List<JournalEntry> findBySourceModuleAndSourceDocumentId(JournalSourceModule sourceModule, UUID sourceDocumentId);

    Optional<JournalEntry> findByEntryNumber(String entryNumber);

    @Query("""
        SELECT je FROM JournalEntry je
        WHERE je.distributorId = :distributorId
          AND (:status IS NULL OR je.status = :status)
          AND (:fromDate IS NULL OR je.entryDate >= :fromDate)
          AND (:toDate IS NULL OR je.entryDate <= :toDate)
          AND (:sourceModule IS NULL OR je.sourceModule = :sourceModule)
        ORDER BY je.entryDate DESC, je.createdAt DESC
        """)
    Page<JournalEntry> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") JournalEntryStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("sourceModule") JournalSourceModule sourceModule,
            Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(je.entryNumber, 12) AS int)), 0) FROM JournalEntry je WHERE je.distributorId = :distributorId AND je.entryNumber LIKE :prefix%")
    int findMaxSequenceForPrefix(@Param("distributorId") UUID distributorId, @Param("prefix") String prefix);
}
