package com.dadcoach.whatsapp.voice;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(VoiceNoteProperties.class)
public class VoiceNoteConfig {

    /** A transcript's JSON grows with the note; room for the longest note allowed. */
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    @Bean
    public WebClient elevenLabsWebClient(VoiceNoteProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return WebClient.builder()
                .baseUrl(properties.getElevenlabsBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }
}
