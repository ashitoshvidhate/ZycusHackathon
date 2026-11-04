package com.stockpulse.repo;

import com.stockpulse.domain.RecommendationSuggestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SuggestionRepository extends JpaRepository<RecommendationSuggestion, String> {
    List<RecommendationSuggestion> findByProductIdOrderByCreatedAtDesc(String productId);
    java.util.Optional<RecommendationSuggestion> findFirstByProductIdAndStatusOrderByCreatedAtDesc(String productId, String status);
    boolean existsByProductIdAndStatus(String productId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecommendationSuggestion s where s.id = :id")
    Optional<RecommendationSuggestion> findForUpdate(@Param("id") String id);
}
