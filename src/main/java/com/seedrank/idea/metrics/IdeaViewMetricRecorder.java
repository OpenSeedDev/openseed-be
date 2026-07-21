package com.seedrank.idea.metrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdeaViewMetricRecorder {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    IdeaViewMetricRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public long record(UUID ideaId, UUID viewerUserId, String guestSessionId) {
        Instant now = clock.instant();
        Instant bucketHour = now.truncatedTo(ChronoUnit.HOURS);
        String guestSessionHash = viewerUserId == null ? hash("guest:" + guestSessionId) : null;
        String viewerKeyHash = viewerUserId == null
                ? guestSessionHash
                : hash("user:" + viewerUserId);

        int inserted = jdbc.update("""
                INSERT INTO idea_view_events(
                    id, idea_id, viewer_user_id, guest_session_hash, viewer_key_hash, bucket_hour, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idea_id, viewer_key_hash, bucket_hour) DO NOTHING
                """, UUID.randomUUID(), ideaId, viewerUserId, guestSessionHash, viewerKeyHash,
                Timestamp.from(bucketHour), Timestamp.from(now));
        if (inserted == 0) {
            return currentCount(ideaId);
        }

        long current = jdbc.queryForObject("""
                INSERT INTO idea_metric_current(idea_id, view_count, updated_at)
                VALUES (?, 1, ?)
                ON CONFLICT (idea_id) DO UPDATE
                   SET view_count = idea_metric_current.view_count + 1,
                       updated_at = EXCLUDED.updated_at
                RETURNING view_count
                """, Long.class, ideaId, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO idea_metric_hourly(idea_id, bucket_hour, view_delta, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (idea_id, bucket_hour) DO UPDATE
                   SET view_delta = idea_metric_hourly.view_delta + 1,
                       updated_at = EXCLUDED.updated_at
                """, ideaId, Timestamp.from(bucketHour), Timestamp.from(now));
        return current;
    }

    private long currentCount(UUID ideaId) {
        Long count = jdbc.query("SELECT view_count FROM idea_metric_current WHERE idea_id=?",
                result -> result.next() ? result.getLong(1) : 0L, ideaId);
        return count == null ? 0L : count;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
