package com.seedrank.ai.job;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seedrank.auth.login.AccessTokenAuthenticator;

@Service
class AiJobCreationService {
    private final AccessTokenAuthenticator authenticator;
    private final AiJobRepository jobs;
    private final Clock clock;
    private final String promptVersion;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    AiJobCreationService(
            AccessTokenAuthenticator authenticator,
            AiJobRepository jobs,
            Clock clock,
            @Value("${app.ai.prompt-version}") String promptVersion) {
        this.authenticator = authenticator;
        this.jobs = jobs;
        this.clock = clock;
        this.promptVersion = promptVersion;
    }

    @Transactional
    AiJobCreationResponse create(
            String authorization,
            String idempotencyKey,
            AiJobCreationRequest request) {
        UUID ownerId = authenticator.authenticate(authorization).userId();
        String validatedKey = validateKey(idempotencyKey);
        String inputSnapshot = snapshot(request.keyword().strip(), request.background().strip());
        UUID newJobId = UUID.randomUUID();

        jobs.insertPendingIfAbsent(
                newJobId, ownerId, inputSnapshot, promptVersion, validatedKey, clock.instant());
        AiJob job = jobs.findByOwnerIdAndIdempotencyKey(ownerId, validatedKey).orElseThrow();
        if (!sameSnapshot(job.inputSnapshot(), inputSnapshot) || !job.promptVersion().equals(promptVersion)) {
            throw new IdempotencyKeyReusedException();
        }
        return new AiJobCreationResponse(job.id());
    }

    private String validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 100 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
        return value;
    }

    private String snapshot(String keyword, String background) {
        try {
            return jsonMapper.writeValueAsString(Map.of("keyword", keyword, "background", background));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI input snapshot serialization failed", exception);
        }
    }

    private boolean sameSnapshot(String first, String second) {
        try {
            return jsonMapper.readTree(first).equals(jsonMapper.readTree(second));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI input snapshot comparison failed", exception);
        }
    }
}
