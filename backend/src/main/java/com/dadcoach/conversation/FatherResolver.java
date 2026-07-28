package com.dadcoach.conversation;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a Father entity by channel identity (senderId + channelId).
 * If the sender is unknown, creates a new Father with status NOT_STARTED.
 *
 * <p>Delegates to the Father domain service for actual entity management.
 */
public interface FatherResolver {

    /**
     * Resolves a father by their external sender identity on a given channel.
     *
     * @param senderId  the external sender identifier (e.g., phone number)
     * @param channelId the communication channel identifier
     * @return the resolved father info, or empty if the sender is unknown
     */
    Optional<ResolvedFather> findBySenderIdentity(String senderId, String channelId);

    /**
     * Creates a new father for an unknown sender. Sets status to NOT_STARTED.
     *
     * @param senderId  the external sender identifier
     * @param channelId the communication channel identifier
     * @return the newly created father info
     */
    ResolvedFather createNewFather(String senderId, String channelId);

    /**
     * Immutable value representing a resolved father with essential info for the pipeline.
     */
    record ResolvedFather(UUID fatherId, String status) {}
}
