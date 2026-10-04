package com.dadcoach.integration.platform.lifecycle;

import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import org.springframework.stereotype.Component;

/**
 * Whose WhatsApp messages must not reach the AI: a DELETED father (his data is being deleted), and a number
 * whose platform deletion is not confirmed yet (after an admin delete the father row is gone at once, but until
 * the platform confirms, a message would resume his old conversation there).
 */
@Component
public class DeletedSenders {

    private final FatherRepository fathers;
    private final PlatformPersonDeletions deletions;

    public DeletedSenders(FatherRepository fathers, PlatformPersonDeletions deletions) {
        this.fathers = fathers;
        this.deletions = deletions;
    }

    public boolean isDeleted(String phone) {
        if (phone == null) {
            return false;
        }
        if (fathers.findByPhone(phone).filter(f -> f.getStatus() == FatherStatus.DELETED).isPresent()) {
            return true;
        }
        return deletions.isPending(phone);
    }
}
