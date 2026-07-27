package com.dadcoach;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
class OpenApiIntegrationTest extends IntegrationTestBase {

    @Test
    void apiDocsEndpointReturnsValidJson() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"");
        assertThat(response.getBody()).contains("\"Dad Coach API\"");
    }
}
