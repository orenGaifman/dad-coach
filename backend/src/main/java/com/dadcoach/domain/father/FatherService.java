package com.dadcoach.domain.father;

import com.dadcoach.channel.WhatsAppEndpoints;
import com.dadcoach.common.PhoneValidator;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.father.FatherStatus;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fathers and their lifecycle. WhatsApp is the only onboarding (D-002): the first save_user_profile of a new
 * number creates him (ONBOARDING) with his WhatsApp endpoint; his first child makes him ACTIVE.
 */
@Service
@Transactional
public class FatherService {

    private static final Logger log = LoggerFactory.getLogger(FatherService.class);

    private final FatherRepository fatherRepository;
    private final WhatsAppEndpoints endpoints;
    private final Clock clock;

    public FatherService(FatherRepository fatherRepository, WhatsAppEndpoints endpoints, Clock clock) {
        this.fatherRepository = fatherRepository;
        this.endpoints = endpoints;
        this.clock = clock;
    }

    /**
     * The father who owns this WhatsApp number, created (ONBOARDING) if there is none. He is writing right now, so
     * his endpoint exists and his 24-hour window is open when this returns.
     */
    public Father findOrCreateFromWhatsApp(String phone) {
        PhoneValidator.requireValidE164(phone);
        Father father = fatherRepository.findByPhone(phone).orElse(null);
        if (father == null) {
            Father created = new Father(phone);
            created.transitionTo(FatherStatus.ONBOARDING);
            try {
                father = fatherRepository.saveAndFlush(created);
                log.info("Father created on WhatsApp: fatherId={}", father.getId());
            } catch (DataIntegrityViolationException raceLost) {
                father = fatherRepository.findByPhone(phone).orElseThrow(() -> raceLost);
            }
        }
        endpoints.recordInbound(father);
        return father;
    }

    /** ONBOARDING (or NOT_STARTED) becomes ACTIVE - once; any other status is left as it is. */
    public void activateIfOnboarding(Father father) {
        if (father.getStatus() == FatherStatus.NOT_STARTED) {
            father.transitionTo(FatherStatus.ONBOARDING);
        }
        if (father.getStatus() == FatherStatus.ONBOARDING) {
            father.transitionTo(FatherStatus.ACTIVE);
            father.setActivationDate(LocalDate.ofInstant(clock.instant(), com.dadcoach.integration.platform.FatherTimezones.of(father)));
            fatherRepository.save(father);
            log.info("Father activated: fatherId={}", father.getId());
        }
    }

    @Transactional(readOnly = true)
    public Father getFather(Long id) {
        return fatherRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Father", id));
    }
}
