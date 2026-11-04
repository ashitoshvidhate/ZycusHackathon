package com.stockpulse.repo;

import com.stockpulse.domain.PricingSuggestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String> {
    List<PricingSuggestion> findByProductIdOrderByCreatedAtDesc(String productId);
    Optional<PricingSuggestion> findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(String productId, String triggerReason, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PricingSuggestion s where s.id = :id")
    Optional<PricingSuggestion> findForUpdate(@Param("id") String id);
}
