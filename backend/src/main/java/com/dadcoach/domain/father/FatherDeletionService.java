package com.dadcoach.domain.father;

import com.dadcoach.common.MaskingUtils;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting a father for good (DECISIONS: person lifecycle). Two doors, one outbox ({@link PlatformPersonDeletions}):
 * <ul>
 *   <li>{@link #requestByFather} - his own request ("DELETE MY DATA" on WhatsApp, the dashboard): DELETED at once
 *       (no message of his reaches the AI any more); his Dad Coach data is purged when the platform confirms it
 *       deleted his person and conversations.</li>
 *   <li>{@link #deleteByOperator} - an operator: his Dad Coach data is purged now; the platform deletion follows
 *       from the outbox, and until it is confirmed his number's messages are dropped.</li>
 * </ul>
 * Both work at any stage (also mid-onboarding) and are idempotent.
 */
@Service
public class FatherDeletionService {

    private static final Logger log = LoggerFactory.getLogger(FatherDeletionService.class);

    private final FatherRepository fathers;
    private final FatherDataPurger purger;
    private final PlatformPersonDeletions platformDeletions;

    public FatherDeletionService(FatherRepository fathers, FatherDataPurger purger, PlatformPersonDeletions platformDeletions) {
        this.fathers = fathers;
        this.purger = purger;
        this.platformDeletions = platformDeletions;
    }

    @Transactional
    public void requestByFather(Father father) {
        if (father.getStatus() != FatherStatus.DELETED) {
            father.transitionTo(FatherStatus.DELETED);
            fathers.save(father);
        }
        platformDeletions.request(father.getId(), father.getPhone(), true);
        log.info("Father deletion requested by the father: fatherId={}", father.getId());
    }

    @Transactional
    public void deleteByOperator(Long fatherId) {
        Father father = fathers.findById(fatherId).orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        String phone = father.getPhone();
        platformDeletions.request(fatherId, phone, false);
        purger.purge(fatherId);
        log.info("Father deleted by an operator: fatherId={}, phone={}", fatherId, MaskingUtils.maskPhone(phone));
    }
}
