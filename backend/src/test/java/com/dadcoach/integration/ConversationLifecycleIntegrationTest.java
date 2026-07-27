package com.dadcoach.integration;

import com.dadcoach.IntegrationTestBase;
import com.dadcoach.conversation.ConversationStatus;
import com.dadcoach.conversation.ConversationType;
import com.dadcoach.domain.conversation.Conversation;
import com.dadcoach.domain.conversation.ConversationService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.father.FatherStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: conversation lifecycle.
 * Create → message exchanges → complete → summary.
 *
 * Verifies conversation state machine, message limit enforcement, and
 * single-active-conversation constraint with a real database.
 */
@Transactional
class ConversationLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private FatherService fatherService;

    @Autowired
    private ConversationService conversationService;

    private Father father;

    @BeforeEach
    void setUp() {
        father = fatherService.createFather("+972501112222");
        fatherService.transitionStatus(father.getId(), FatherStatus.ONBOARDING, "Onboarding");
        father = fatherService.activateFather(father.getId());
    }

    @Test
    void conversationLifecycle_createExchangesComplete() {
        // Step 1: Start a daily coaching conversation
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Conversation conversation = conversationService.startConversation(
                father.getId(), ConversationType.DAILY_COACHING,
                "Deliver daily coaching content", expiresAt
        );
        assertThat(conversation.getId()).isNotNull();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.getType()).isEqualTo(ConversationType.DAILY_COACHING);

        // Step 2: Record outbound messages (simulating exchanges)
        for (int i = 0; i < 3; i++) {
            conversationService.recordOutboundMessage(conversation.getId());
        }
        Conversation updated = conversationService.getConversation(conversation.getId());
        assertThat(updated.getMessageCount()).isEqualTo(3);

        // Step 3: Complete the conversation with a summary
        Conversation completed = conversationService.completeConversation(
                conversation.getId(), "Father engaged positively, discussed morning routine"
        );
        assertThat(completed.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(completed.getSummary()).contains("morning routine");

        // Step 4: Verify no active conversations remain
        Optional<Conversation> active = conversationService.getActiveConversation(father.getId());
        assertThat(active).isEmpty();
    }

    @Test
    void conversationLifecycle_messageLimit_autoCompletes() {
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Conversation conversation = conversationService.startConversation(
                father.getId(), ConversationType.FOLLOW_UP,
                "Follow up on mission", expiresAt
        );

        // Send MAX messages (8) — should auto-complete
        for (int i = 0; i < 8; i++) {
            conversationService.recordOutboundMessage(conversation.getId());
        }

        Conversation result = conversationService.getConversation(conversation.getId());
        assertThat(result.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(result.getMessageCount()).isEqualTo(8);
    }

    @Test
    void conversationLifecycle_difficultSituationPreempts() {
        // Start a regular conversation
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Conversation regular = conversationService.startConversation(
                father.getId(), ConversationType.DAILY_COACHING,
                "Daily coaching", expiresAt
        );
        assertThat(regular.getStatus()).isEqualTo(ConversationStatus.ACTIVE);

        // Start a DIFFICULT_SITUATION — should preempt the existing one
        Conversation urgent = conversationService.startConversation(
                father.getId(), ConversationType.DIFFICULT_SITUATION,
                "Father reported conflict", expiresAt
        );
        assertThat(urgent.getStatus()).isEqualTo(ConversationStatus.ACTIVE);

        // Original conversation should now be COMPLETED
        Conversation preempted = conversationService.getConversation(regular.getId());
        assertThat(preempted.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
    }
}
