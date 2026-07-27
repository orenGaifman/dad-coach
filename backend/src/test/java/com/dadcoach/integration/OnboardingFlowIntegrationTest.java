package com.dadcoach.integration;

import com.dadcoach.IntegrationTestBase;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.memory.Memory;
import com.dadcoach.domain.memory.MemoryService;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.father.OnboardingState;
import com.dadcoach.memory.MemoryCategory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: full onboarding flow.
 * Father creation → status transitions → memory creation.
 *
 * Verifies services are wired correctly and work with a real PostgreSQL database.
 */
@Transactional
class OnboardingFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private FatherService fatherService;

    @Autowired
    private ChildService childService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private FatherRepository fatherRepository;

    @Test
    void fullOnboardingFlow_fatherCreationToActiveWithMemory() {
        // Step 1: Create a father (arrives from WhatsApp)
        Father father = fatherService.createFather("+972501234567");
        assertThat(father.getId()).isNotNull();
        assertThat(father.getStatus()).isEqualTo(FatherStatus.NOT_STARTED);
        assertThat(father.getPhone()).isEqualTo("+972501234567");

        // Step 2: Transition to ONBOARDING
        Father onboarding = fatherService.transitionStatus(father.getId(), FatherStatus.ONBOARDING, "Onboarding initiated");
        assertThat(onboarding.getStatus()).isEqualTo(FatherStatus.ONBOARDING);

        // Step 3: Update profile during onboarding
        fatherService.updateProfile(father.getId(), "Oren", "Asia/Jerusalem", "he");
        Father updated = fatherService.getFather(father.getId());
        assertThat(updated.getDisplayName()).isEqualTo("Oren");

        // Step 4: Register a child
        Child child = childService.createChild(father.getId(), "Yoav", LocalDate.now().minusYears(5));
        assertThat(child.getId()).isNotNull();
        assertThat(child.getName()).isEqualTo("Yoav");

        // Step 5: Activate the father (ONBOARDING → ACTIVE)
        Father active = fatherService.activateFather(father.getId());
        assertThat(active.getStatus()).isEqualTo(FatherStatus.ACTIVE);
        assertThat(active.getActivationDate()).isEqualTo(LocalDate.now());

        // Step 6: Create a memory (onboarding-extracted fact)
        Memory memory = memoryService.createMemory(
                father.getId(), child.getId(),
                MemoryCategory.IDENTITY_FACT,
                "Yoav loves dinosaurs and playing in the park",
                7, new BigDecimal("1.0")
        );
        assertThat(memory.getId()).isNotNull();
        assertThat(memory.getContent()).contains("dinosaurs");

        // Verify data persisted to database
        assertThat(fatherRepository.findByPhone("+972501234567")).isPresent();
        assertThat(memoryService.getActiveMemoryCount(father.getId())).isEqualTo(1);
    }
}
