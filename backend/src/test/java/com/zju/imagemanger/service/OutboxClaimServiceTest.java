package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.OutboxEvent;
import com.zju.imagemanger.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxClaimServiceTest {
    @Test
    void claimedEventGetsPublishingStatusAndLease() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEvent event = new OutboxEvent();
        event.setId(9L);
        when(repository.claimable(any())).thenReturn(List.of(event));

        List<OutboxEvent> claimed = new OutboxClaimService(repository).claimBatch();

        assertEquals(1, claimed.size());
        assertEquals("PUBLISHING", event.getStatus());
        assertTrue(event.getLeaseUntil().isAfter(LocalDateTime.now()));
        verify(repository).claimable(any());
    }
}
