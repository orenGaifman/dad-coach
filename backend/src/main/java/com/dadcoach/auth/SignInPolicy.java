package com.dadcoach.auth;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may hold a dashboard session (checked when a link is requested, when it is consumed, and on every request).
 * A father may unless he was deactivated by the team (PAUSED), deleted, or his deletion is still in progress.
 * A staff user may while active. One phone can be both: the session then carries both capabilities.
 */
@Component
public class SignInPolicy {

    private final FatherRepository fathers;
    private final StaffUserRepository staff;
    private final PlatformPersonDeletions deletions;

    public SignInPolicy(FatherRepository fathers, StaffUserRepository staff, PlatformPersonDeletions deletions) {
        this.fathers = fathers;
        this.staff = staff;
        this.deletions = deletions;
    }

    public static boolean fatherMaySignIn(Father father) {
        return father != null && father.getStatus() != FatherStatus.PAUSED && father.getStatus() != FatherStatus.DELETED;
    }

    /** The subject a phone signs in as, if any (an unknown number and a blocked one look the same). */
    @Transactional(readOnly = true)
    public Optional<SignInSubject> forPhone(String e164) {
        Long fatherId = fathers.findByPhone(e164).filter(this::fatherAllowed).map(Father::getId).orElse(null);
        UUID staffId = staff.findByPhone(e164).filter(StaffUser::isActive).map(StaffUser::getId).orElse(null);
        return fatherId == null && staffId == null ? Optional.empty() : Optional.of(new SignInSubject(fatherId, staffId));
    }

    /** The live capabilities of a subject: each part kept only while it may still sign in. */
    @Transactional(readOnly = true)
    public Optional<DashboardPrincipal> resolve(UUID sessionId, SignInSubject subject) {
        Optional<Father> father = subject.fatherId() == null ? Optional.empty()
                : fathers.findById(subject.fatherId()).filter(this::fatherAllowed);
        Optional<StaffUser> staffUser = subject.staffUserId() == null ? Optional.empty()
                : staff.findById(subject.staffUserId()).filter(StaffUser::isActive);
        if (father.isEmpty() && staffUser.isEmpty()) {
            return Optional.empty();
        }
        String name = staffUser.map(StaffUser::getDisplayName)
                .or(() -> father.map(Father::getDisplayName))
                .filter(n -> !n.isBlank())
                .orElse("");
        return Optional.of(new DashboardPrincipal(sessionId, father.map(Father::getId).orElse(null),
                staffUser.map(StaffUser::getId).orElse(null), name));
    }

    private boolean fatherAllowed(Father father) {
        return fatherMaySignIn(father) && !deletions.isPending(father.getPhone());
    }
}
