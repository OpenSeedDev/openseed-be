ALTER TABLE ideas
    ADD COLUMN visibility VARCHAR(20),
    ADD COLUMN current_unit_price INTEGER,
    ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE ideas DROP CONSTRAINT ck_ideas_status;
ALTER TABLE ideas ADD CONSTRAINT ck_ideas_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
ALTER TABLE ideas ADD CONSTRAINT ck_ideas_visibility
    CHECK (visibility IS NULL OR visibility IN ('PUBLIC', 'SEMI_PUBLIC', 'MATCHING'));
ALTER TABLE ideas ADD CONSTRAINT ck_ideas_publish_fields CHECK (
    (status = 'DRAFT' AND visibility IS NULL AND current_unit_price IS NULL AND published_at IS NULL)
    OR (status IN ('PUBLISHED', 'ARCHIVED') AND visibility IS NOT NULL
        AND current_unit_price > 0 AND published_at IS NOT NULL)
);

CREATE TABLE idea_versions (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    summary VARCHAR(200) NOT NULL,
    problem VARCHAR(2000) NOT NULL,
    target_customer VARCHAR(1000) NOT NULL,
    solution VARCHAR(2000) NOT NULL,
    business_model VARCHAR(2000) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    validation_questions TEXT NOT NULL,
    editor_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idea_versions_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_idea_versions_editor FOREIGN KEY (editor_id) REFERENCES users (id),
    CONSTRAINT uq_idea_versions_number UNIQUE (idea_id, version_number),
    CONSTRAINT ck_idea_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_idea_versions_visibility CHECK (visibility IN ('PUBLIC', 'SEMI_PUBLIC', 'MATCHING'))
);

CREATE TABLE idea_timeline_events (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idea_timeline_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_idea_timeline_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_idea_timeline_event_type CHECK (event_type IN ('PUBLISHED'))
);

CREATE INDEX idx_idea_timeline_events_idea_created_at
    ON idea_timeline_events (idea_id, created_at, id);
