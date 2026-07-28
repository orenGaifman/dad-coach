package com.dadcoach.onboarding.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SessionStatus} enum.
 */
class SessionStatusTest {

    @Test
    void inProgressIsNotTerminal() {
        assertFalse(SessionStatus.IN_PROGRESS.isTerminal());
    }

    @Test
    void completedIsTerminal() {
        assertTrue(SessionStatus.COMPLETED.isTerminal());
    }

    @Test
    void expiredIsTerminal() {
        assertTrue(SessionStatus.EXPIRED.isTerminal());
    }

    @Test
    void abandonedIsTerminal() {
        assertTrue(SessionStatus.ABANDONED.isTerminal());
    }

    @Test
    void inProgressCanTransitionToCompleted() {
        assertTrue(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.COMPLETED));
    }

    @Test
    void inProgressCanTransitionToExpired() {
        assertTrue(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.EXPIRED));
    }

    @Test
    void inProgressCanTransitionToAbandoned() {
        assertTrue(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.ABANDONED));
    }

    @Test
    void inProgressCannotTransitionToInProgress() {
        assertFalse(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.IN_PROGRESS));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "EXPIRED", "ABANDONED"})
    void terminalStatusesCannotTransition(SessionStatus terminalStatus) {
        assertFalse(terminalStatus.canTransitionTo(SessionStatus.IN_PROGRESS));
        assertFalse(terminalStatus.canTransitionTo(SessionStatus.COMPLETED));
        assertFalse(terminalStatus.canTransitionTo(SessionStatus.EXPIRED));
        assertFalse(terminalStatus.canTransitionTo(SessionStatus.ABANDONED));
    }
}
