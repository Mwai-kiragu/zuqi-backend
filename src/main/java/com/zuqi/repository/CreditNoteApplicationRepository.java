package com.zuqi.repository;

import com.zuqi.domain.returns.CreditNoteApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditNoteApplicationRepository extends JpaRepository<CreditNoteApplication, UUID> {

    List<CreditNoteApplication> findByCreditNoteId(UUID creditNoteId);

    List<CreditNoteApplication> findByInvoiceId(UUID invoiceId);

    @Query("SELECT COALESCE(SUM(a.amountApplied), 0) FROM CreditNoteApplication a WHERE a.invoice.id = :invoiceId")
    BigDecimal sumAppliedByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
