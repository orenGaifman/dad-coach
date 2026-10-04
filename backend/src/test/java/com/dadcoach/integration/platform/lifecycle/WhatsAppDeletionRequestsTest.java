package com.dadcoach.integration.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** "DELETE MY DATA" on WhatsApp is a father's deletion request - the exact phrase only. */
class WhatsAppDeletionRequestsTest {

    private final FatherRepository fathers = mock(FatherRepository.class);
    private final PlatformPersonDeletions deletions = mock(PlatformPersonDeletions.class);
    private final WhatsAppDeletionRequests requests = new WhatsAppDeletionRequests(fathers, deletions);

    @Test
    @DisplayName("the exact phrase, whatever its case, spacing or closing period; nothing else")
    void onlyTheExactPhrase() {
        assertThat(WhatsAppDeletionRequests.isRequest("DELETE MY DATA")).isTrue();
        assertThat(WhatsAppDeletionRequests.isRequest("  delete   my data. ")).isTrue();
        assertThat(WhatsAppDeletionRequests.isRequest("Delete my data!")).isTrue();
        assertThat(WhatsAppDeletionRequests.isRequest("should I delete my data?")).isFalse();
        assertThat(WhatsAppDeletionRequests.isRequest("delete my data please")).isFalse();
        assertThat(WhatsAppDeletionRequests.isRequest(null)).isFalse();
    }

    @Test
    @DisplayName("a known father is DELETED at once and his platform deletion and purge are requested")
    void knownFather() {
        Father father = father(7L, FatherStatus.ACTIVE);

        assertThat(requests.handle("+972501230007")).isEqualTo(WhatsAppDeletionRequests.CONFIRMATION);

        assertThat(father.getStatus()).isEqualTo(FatherStatus.DELETED);
        verify(deletions).request(7L, "+972501230007", true);
    }

    @Test
    @DisplayName("no account, or a status that cannot be deleted yet (onboarding): a reply, nothing deleted")
    void noAccountOrNotYet() {
        when(fathers.findByPhone("+972501230008")).thenReturn(Optional.empty());
        assertThat(requests.handle("+972501230008")).isEqualTo(WhatsAppDeletionRequests.NO_ACCOUNT);
        father(9L, FatherStatus.ONBOARDING);
        assertThat(requests.handle("+972501230009")).isEqualTo(WhatsAppDeletionRequests.MANUAL);
        verify(deletions, never()).request(anyLong(), any(), anyBoolean());
    }

    private Father father(long id, FatherStatus status) {
        String phone = String.format("+97250123%04d", id);
        Father father = new Father(phone);
        ReflectionTestUtils.setField(father, "id", id);
        father.setStatus(status);
        when(fathers.findByPhone(phone)).thenReturn(Optional.of(father));
        return father;
    }
}
