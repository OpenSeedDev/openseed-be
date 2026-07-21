CREATE TABLE message_threads (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    company_profile_id UUID NOT NULL,
    author_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_message_threads_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_threads_company_profile FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_threads_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT uq_message_threads_participants UNIQUE (idea_id, company_profile_id, author_id)
);

CREATE INDEX idx_message_threads_company_updated
    ON message_threads (company_profile_id, updated_at DESC, id DESC);

CREATE INDEX idx_message_threads_author_updated
    ON message_threads (author_id, updated_at DESC, id DESC);
