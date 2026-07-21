CREATE TABLE idea_view_events (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    viewer_user_id UUID,
    guest_session_hash CHAR(64),
    viewer_key_hash CHAR(64) NOT NULL,
    bucket_hour TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idea_view_events_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_idea_view_events_user FOREIGN KEY (viewer_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_idea_view_events_dedup UNIQUE (idea_id, viewer_key_hash, bucket_hour),
    CONSTRAINT ck_idea_view_events_viewer CHECK (
        (viewer_user_id IS NOT NULL AND guest_session_hash IS NULL)
        OR (viewer_user_id IS NULL AND guest_session_hash IS NOT NULL)
    )
);

CREATE TABLE idea_metric_current (
    idea_id UUID PRIMARY KEY,
    view_count BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idea_metric_current_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT ck_idea_metric_current_view_count CHECK (view_count >= 0)
);

CREATE TABLE idea_metric_hourly (
    idea_id UUID NOT NULL,
    bucket_hour TIMESTAMP WITH TIME ZONE NOT NULL,
    view_delta BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (idea_id, bucket_hour),
    CONSTRAINT fk_idea_metric_hourly_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT ck_idea_metric_hourly_view_delta CHECK (view_delta > 0)
);

CREATE INDEX idx_idea_view_events_idea_created_at
    ON idea_view_events (idea_id, created_at);
