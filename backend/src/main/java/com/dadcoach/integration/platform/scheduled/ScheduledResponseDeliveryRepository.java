package com.dadcoach.integration.platform.scheduled;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScheduledResponseDeliveryRepository extends JpaRepository<ScheduledResponseDelivery, UUID> {

    Optional<ScheduledResponseDelivery> findByIdempotencyKey(String idempotencyKey);
}
