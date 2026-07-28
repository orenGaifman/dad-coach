package com.dadcoach.onboarding.activation;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivationListener Unit Tests")
class ActivationListenerTest {

    @Mock private FatherRepository fatherRepository;
    @Mock private ActivationService activationService;

    private ActivationListener listener;

    private static final Long FATHER_ID = 1L;
    private static final String PHONE = "+972501234567";

    @BeforeEach
    void setUp() {
        listener = new ActivationListener(fatherRepository, activationService);
    }

    @Nested
    @DisplayName("interceptIfOnboarding")
    class InterceptIfOnboardingTests {

        @Test
        @DisplayName("intercepts message from ONBOARDING father")
        void interceptsOnboardingFather() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.ONBOARDING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "🚀 START");

            assertThat(intercepted).isTrue();
            verify(activationService).handleActivationMessage(FATHER_ID, "🚀 START");
        }

        @Test
        @DisplayName("intercepts ANY message from ONBOARDING father, not just START")
        void interceptsAnyMessage() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.ONBOARDING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "שלום מה קורה?");

            assertThat(intercepted).isTrue();
            verify(activationService).handleActivationMessage(FATHER_ID, "שלום מה קורה?");
        }

        @Test
        @DisplayName("does not intercept message from ACTIVE father")
        void doesNotInterceptActiveFather() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.ACTIVE);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "hello");

            assertThat(intercepted).isFalse();
            verify(activationService, never()).handleActivationMessage(any(), any());
        }

        @Test
        @DisplayName("does not intercept message from PAUSED father")
        void doesNotInterceptPausedFather() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.PAUSED);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "hello");

            assertThat(intercepted).isFalse();
            verify(activationService, never()).handleActivationMessage(any(), any());
        }

        @Test
        @DisplayName("returns false for null fatherId")
        void returnsFalseForNullFatherId() {
            boolean intercepted = listener.interceptIfOnboarding(null, "hello");

            assertThat(intercepted).isFalse();
            verify(fatherRepository, never()).findById(any());
        }

        @Test
        @DisplayName("returns false when father not found")
        void returnsFalseWhenFatherNotFound() {
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.empty());

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "hello");

            assertThat(intercepted).isFalse();
            verify(activationService, never()).handleActivationMessage(any(), any());
        }

        @Test
        @DisplayName("returns true even if activation handler throws")
        void returnsTrueEvenOnError() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.ONBOARDING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            doThrow(new RuntimeException("activation failed"))
                    .when(activationService).handleActivationMessage(FATHER_ID, "test");

            boolean intercepted = listener.interceptIfOnboarding(FATHER_ID, "test");

            // Should still return true to prevent re-processing
            assertThat(intercepted).isTrue();
        }
    }

    @Nested
    @DisplayName("interceptByPhoneIfOnboarding")
    class InterceptByPhoneTests {

        @Test
        @DisplayName("resolves father by phone and intercepts if ONBOARDING")
        void resolvesByPhoneAndIntercepts() {
            Father father = new Father(PHONE);
            father.setId(FATHER_ID);
            father.setStatus(FatherStatus.ONBOARDING);
            when(fatherRepository.findByPhone(PHONE)).thenReturn(Optional.of(father));
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

            boolean intercepted = listener.interceptByPhoneIfOnboarding(PHONE, "hello");

            assertThat(intercepted).isTrue();
            verify(activationService).handleActivationMessage(FATHER_ID, "hello");
        }

        @Test
        @DisplayName("returns false for unknown phone number")
        void returnsFalseForUnknownPhone() {
            when(fatherRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

            boolean intercepted = listener.interceptByPhoneIfOnboarding(PHONE, "hello");

            assertThat(intercepted).isFalse();
        }

        @Test
        @DisplayName("returns false for null phone")
        void returnsFalseForNullPhone() {
            boolean intercepted = listener.interceptByPhoneIfOnboarding(null, "hello");

            assertThat(intercepted).isFalse();
            verify(fatherRepository, never()).findByPhone(any());
        }

        @Test
        @DisplayName("returns false for blank phone")
        void returnsFalseForBlankPhone() {
            boolean intercepted = listener.interceptByPhoneIfOnboarding("  ", "hello");

            assertThat(intercepted).isFalse();
            verify(fatherRepository, never()).findByPhone(any());
        }
    }
}
