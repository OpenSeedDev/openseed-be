package com.seedrank.feedback.list;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record FeedbackCursor(boolean accepted, Instant createdAt, UUID id) {

    String encode() {
        String value = (accepted ? "A" : "N") + ":" + createdAt.getEpochSecond()
                + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static FeedbackCursor decode(String encoded) {
        if (encoded == null) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 4 || !(parts[0].equals("A") || parts[0].equals("N"))) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new FeedbackCursor(
                    parts[0].equals("A"),
                    Instant.ofEpochSecond(Long.parseLong(parts[1]), Long.parseLong(parts[2])),
                    UUID.fromString(parts[3]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("invalid cursor", exception);
        }
    }
}
