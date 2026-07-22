CREATE TABLE company_interests (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    company_profile_id UUID NOT NULL,
    interested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_company_interests_idea
        FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_interests_company_profile
        FOREIGN KEY (company_profile_id) REFERENCES company_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uq_company_interests_idea_company UNIQUE (idea_id, company_profile_id)
);

CREATE INDEX idx_company_interests_idea_interested_at
    ON company_interests (idea_id, interested_at DESC, id DESC);

ALTER TABLE idea_timeline_events DROP CONSTRAINT ck_idea_timeline_event_type;
ALTER TABLE idea_timeline_events ADD CONSTRAINT ck_idea_timeline_event_type
    CHECK (event_type IN (
        'PUBLISHED', 'UPDATED', 'COMPANY_INTERESTED', 'COMPANY_INTEREST_REMOVED'
    ));
