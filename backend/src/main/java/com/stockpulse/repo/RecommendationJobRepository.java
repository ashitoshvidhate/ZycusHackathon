package com.stockpulse.repo;

import com.stockpulse.domain.RecommendationJob;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RecommendationJobRepository extends JpaRepository<RecommendationJob,String> {
    long countByProductIdAndStatus(String productId,String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from RecommendationJob j where j.id=:id")
    Optional<RecommendationJob> findForUpdate(@Param("id") String id);
    List<RecommendationJob> findTop100ByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(List<String> statuses,int attempts);
}
