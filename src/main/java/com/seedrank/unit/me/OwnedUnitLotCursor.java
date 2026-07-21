package com.seedrank.unit.me;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record OwnedUnitLotCursor(Instant purchasedAt, UUID id) {
    String encode() {
        String value = purchasedAt.getEpochSecond() + ":" + purchasedAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static OwnedUnitLotCursor decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new OwnedUnitLotCursor(
                    Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("invalid cursor", exception);
        }
    }
}
