CREATE TABLE idea_likes (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idea_likes_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_idea_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_idea_likes_idea_user UNIQUE (idea_id, user_id)
);

CREATE INDEX idx_idea_likes_user_created_at
    ON idea_likes (user_id, created_at DESC, id DESC);
