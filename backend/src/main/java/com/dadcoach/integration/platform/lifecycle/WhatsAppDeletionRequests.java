package com.dadcoach.integration.platform.lifecycle;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public data-deletion page tells a father to send "DELETE MY DATA" on WhatsApp. That exact message (case,
 * spacing and a closing period or "!" aside) from a known father is his deletion request: the same path as
 * deleting his account in the dashboard - DELETED at once (at any stage, also mid-onboarding), then the platform deletes his conversations and his Dad Coach
 * data is purged ({@link PlatformPersonDeletions}). It never reaches the AI. Anything else is an ordinary message.
 */
@Component
public class WhatsAppDeletionRequests {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeletionRequests.class);
    /** "DELETE MY DATA" stays accepted (Meta's data-deletion instructions name it); the Hebrew phrases are the ones fathers use. */
    static final java.util.Set<String> PHRASES = java.util.Set.of("delete my data", "מחק את המידע שלי", "מחקו את המידע שלי",
            "מחיקת המידע שלי", "תמחק את המידע שלי");

    static final String CONFIRMATION = "קיבלנו את הבקשה. החשבון שלך בדאד קואץ׳ וכל המידע שלך - מספר הטלפון, השיחות וההעדפות - "
            + "נמחקים עכשיו. זו ההודעה האחרונה שתקבל מאיתנו.";
    static final String NO_ACCOUNT = "קיבלנו את הבקשה. למספר הזה אין חשבון בדאד קואץ׳. "
            + "כדי למחוק כל מידע אחר שאולי שמור אצלנו, כתוב ל-oren26g@gmail.com עם הנושא \"בקשת מחיקת מידע\".";

    private final FatherRepository fathers;
    private final com.dadcoach.domain.father.FatherDeletionService deletions;

    public WhatsAppDeletionRequests(FatherRepository fathers, com.dadcoach.domain.father.FatherDeletionService deletions) {
        this.fathers = fathers;
        this.deletions = deletions;
    }

    public static boolean isRequest(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().replaceAll("\\s+", " ").replaceAll("[.!]+$", "").toLowerCase(Locale.ROOT);
        return PHRASES.contains(normalized);
    }

    /** Handles a deletion request from this number. @return the reply to send */
    @Transactional
    public String handle(String phone) {
        Optional<Father> found = fathers.findByPhone(phone);
        if (found.isEmpty()) {
            log.info("WhatsApp deletion request from a number with no account");
            return NO_ACCOUNT;
        }
        Father father = found.get();
        deletions.requestByFather(father);
        log.info("WhatsApp deletion request accepted: fatherId={}", father.getId());
        return CONFIRMATION;
    }
}
