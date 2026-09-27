package com.dadcoach.integration.platform;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformUserResolverTest {

    @Mock
    private FatherRepository fatherRepository;

    @Test
    void resolvesAWhatsAppUserIdByPhone() {
        Father father = new Father("+972501234567");
        when(fatherRepository.findByPhone("+972501234567")).thenReturn(Optional.of(father));

        assertThat(new PlatformUserResolver(fatherRepository).resolve("whatsapp:+972501234567")).contains(father);
    }

    @Test
    void rejectsMalformedAndUnsupportedIds() {
        PlatformUserResolver resolver = new PlatformUserResolver(fatherRepository);

        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("+972501234567")).isEmpty();
        assertThat(resolver.resolve("sms:+972501234567")).isEmpty();
        verifyNoInteractions(fatherRepository);
    }
}
