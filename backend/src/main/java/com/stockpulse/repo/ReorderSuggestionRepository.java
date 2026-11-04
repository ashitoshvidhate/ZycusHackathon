package com.stockpulse.repo;

import com.stockpulse.domain.ReorderSuggestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String> {
    List<ReorderSuggestion> findByProductIdOrderByCreatedAtDesc(String productId);
    Optional<ReorderSuggestion> findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(String productId, String triggerReason, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReorderSuggestion s where s.id = :id")
    Optional<ReorderSuggestion> findForUpdate(@Param("id") String id);
}
