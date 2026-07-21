package com.seedrank.point.me;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record PointLedgerCursor(Instant createdAt, UUID id) {

    String encode() {
        String value = createdAt.getEpochSecond() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static PointLedgerCursor decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid cursor");
            }
            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new PointLedgerCursor(createdAt, UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("invalid cursor", exception);
        }
    }
}
