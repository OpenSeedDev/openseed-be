CREATE TABLE idea_keywords (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT fk_idea_keywords_idea
        FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT ck_idea_keywords_not_blank CHECK (btrim(keyword) <> '')
);

CREATE UNIQUE INDEX uk_idea_keywords_idea_normalized_keyword
    ON idea_keywords (idea_id, lower(btrim(keyword)));

