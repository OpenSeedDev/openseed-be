CREATE TABLE ranking_publish_lock (
    id SMALLINT PRIMARY KEY,
    CONSTRAINT ck_ranking_publish_lock_singleton CHECK (id = 1)
);

INSERT INTO ranking_publish_lock(id) VALUES (1);

CREATE TABLE ranking_runs (
    target_hour TIMESTAMP WITH TIME ZONE PRIMARY KEY,
    idea_count INTEGER NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ranking_runs_utc_hour CHECK (
        target_hour = date_trunc('hour', target_hour)
    ),
    CONSTRAINT ck_ranking_runs_idea_count CHECK (idea_count >= 0)
);

CREATE TABLE ranking_current (
    idea_id UUID PRIMARY KEY,
    rank_position INTEGER NOT NULL,
    previous_rank_position INTEGER,
    total_score DOUBLE PRECISION NOT NULL,
    components JSONB NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ranking_current_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_ranking_current_run FOREIGN KEY (calculated_at) REFERENCES ranking_runs (target_hour),
    CONSTRAINT uq_ranking_current_position UNIQUE (rank_position),
    CONSTRAINT ck_ranking_current_position CHECK (rank_position > 0),
    CONSTRAINT ck_ranking_current_previous_position CHECK (
        previous_rank_position IS NULL OR previous_rank_position > 0
    ),
    CONSTRAINT ck_ranking_current_total_score CHECK (total_score >= 0),
    CONSTRAINT ck_ranking_current_components CHECK (jsonb_typeof(components) = 'object')
);

CREATE INDEX idx_ranking_current_calculated_at
    ON ranking_current (calculated_at);
