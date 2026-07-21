ALTER TABLE idea_timeline_events DROP CONSTRAINT ck_idea_timeline_event_type;
ALTER TABLE idea_timeline_events ADD CONSTRAINT ck_idea_timeline_event_type
    CHECK (event_type IN ('PUBLISHED', 'UPDATED'));
