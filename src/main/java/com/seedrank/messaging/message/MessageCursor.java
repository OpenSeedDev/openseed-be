package com.seedrank.messaging.message;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record MessageCursor(Instant sentAt, UUID id) {

    String encode() {
        String value = sentAt.getEpochSecond() + ":" + sentAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static MessageCursor decode(String encoded) {
        if (encoded == null) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException("invalid cursor");
            return new MessageCursor(
                    Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("invalid cursor", exception);
        }
    }
}
