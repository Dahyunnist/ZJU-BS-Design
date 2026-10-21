package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.LocalDateTime;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    long countByStatus(String status);
    @Query(value = "SELECT * FROM outbox_events WHERE (status = 'PENDING' AND next_attempt_at <= :now) OR (status = 'PUBLISHING' AND lease_until <= :now) ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> claimable(@Param("now") LocalDateTime now);
}
