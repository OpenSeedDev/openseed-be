ALTER TABLE ideas
    DROP CONSTRAINT ck_ideas_status;

ALTER TABLE ideas
    ADD CONSTRAINT ck_ideas_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
