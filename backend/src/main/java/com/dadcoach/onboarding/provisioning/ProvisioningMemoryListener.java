package com.dadcoach.onboarding.provisioning;

import com.dadcoach.domain.memory.MemoryService;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.onboarding.session.WizardData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

/**
 * Listener that creates initial onboarding memories after provisioning completes.
 * Triggered asynchronously after the provisioning transaction commits.
 *
 * <p>Creates memories with importance_score=8 and confidence_score=1.0 for:
 * <ul>
 *   <li>Father identity facts (name, language, timezone)</li>
 *   <li>Children information (names, ages)</li>
 *   <li>Goals information</li>
 *   <li>Communication preferences</li>
 * </ul>
 */
@Component
public class ProvisioningMemoryListener {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningMemoryListener.class);

    private static final int IMPORTANCE_SCORE = 8;
    private static final BigDecimal CONFIDENCE_SCORE = BigDecimal.ONE;

    private final MemoryService memoryService;

    public ProvisioningMemoryListener(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Handles the provisioning completed event by creating initial memories.
     * Runs asynchronously after the transaction commits to avoid blocking the provisioning response.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProvisioningCompleted(ProvisioningCompletedEvent event) {
        Long fatherId = event.fatherId();
        WizardData wizardData = event.wizardData();

        log.info("Creating initial onboarding memories for father_id={}", fatherId);

        try {
            createIdentityMemory(fatherId, wizardData);
            createChildrenMemory(fatherId, wizardData);
            createGoalsMemory(fatherId, wizardData);
            createPreferencesMemory(fatherId, wizardData);

            log.info("Successfully created onboarding memories for father_id={}", fatherId);
        } catch (Exception e) {
            // Log but don't fail — memory creation is best-effort post-provisioning
            log.error("Failed to create onboarding memories for father_id={}: {}",
                    fatherId, e.getMessage(), e);
        }
    }

    private void createIdentityMemory(Long fatherId, WizardData wizardData) {
        StringBuilder content = new StringBuilder();
        content.append("Father's name: ").append(wizardData.getDisplayName());
        if (wizardData.getLanguage() != null) {
            content.append(". Preferred language: ").append(wizardData.getLanguage());
        }
        if (wizardData.getTimezone() != null) {
            content.append(". Timezone: ").append(wizardData.getTimezone());
        }

        memoryService.createMemory(fatherId, null, MemoryCategory.IDENTITY,
                content.toString(), IMPORTANCE_SCORE, CONFIDENCE_SCORE);
    }

    private void createChildrenMemory(Long fatherId, WizardData wizardData) {
        List<WizardData.ChildData> children = wizardData.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }

        StringBuilder content = new StringBuilder("Children: ");
        for (int i = 0; i < children.size(); i++) {
            WizardData.ChildData child = children.get(i);
            if (i > 0) content.append(", ");
            content.append(child.getName());
            if (child.getBirthDate() != null) {
                content.append(" (born ").append(child.getBirthDate()).append(")");
            }
        }

        memoryService.createMemory(fatherId, null, MemoryCategory.IDENTITY,
                content.toString(), IMPORTANCE_SCORE, CONFIDENCE_SCORE);
    }

    private void createGoalsMemory(Long fatherId, WizardData wizardData) {
        List<String> goals = wizardData.getGoals();
        if (goals == null || goals.isEmpty()) {
            return;
        }

        String content = "Parenting goals: " + String.join(", ", goals);
        memoryService.createMemory(fatherId, null, MemoryCategory.GOAL,
                content, IMPORTANCE_SCORE, CONFIDENCE_SCORE);
    }

    private void createPreferencesMemory(Long fatherId, WizardData wizardData) {
        var preferences = wizardData.getPreferences();
        if (preferences == null || preferences.isEmpty()) {
            return;
        }

        StringBuilder content = new StringBuilder("Communication preferences: ");
        if (preferences.containsKey("coaching_style")) {
            content.append("Coaching style: ").append(preferences.get("coaching_style")).append(". ");
        }
        if (preferences.containsKey("notification_frequency")) {
            content.append("Frequency: ").append(preferences.get("notification_frequency")).append(". ");
        }

        memoryService.createMemory(fatherId, null, MemoryCategory.PREFERENCE,
                content.toString(), IMPORTANCE_SCORE, CONFIDENCE_SCORE);
    }
}
