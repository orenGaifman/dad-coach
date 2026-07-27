package com.dadcoach.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CoachingSession} entities.
 */
@Repository
public interface CoachingSessionRepository extends JpaRepository<CoachingSession, Long> {

    /**
     * Find the coaching session for a specific conversation.
     *
     * @param conversationId the conversation ID
     * @return the coaching session, if any
     */
    Optional<CoachingSession> findByConversationId(Long conversationId);

    /**
     * Find all coaching sessions for a father ordered by creation time descending.
     *
     * @param fatherId the father ID
     * @return list of coaching sessions
     */
    List<CoachingSession> findByFatherIdOrderByCreatedAtDesc(Long fatherId);
}
