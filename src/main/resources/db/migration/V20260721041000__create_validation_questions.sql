CREATE TABLE validation_questions (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    question TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_validation_questions_idea
        FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT uq_validation_questions_idea_sort_order UNIQUE (idea_id, sort_order),
    CONSTRAINT ck_validation_questions_question_not_blank CHECK (btrim(question) <> ''),
    CONSTRAINT ck_validation_questions_sort_order CHECK (sort_order BETWEEN 1 AND 3)
);

CREATE INDEX idx_validation_questions_idea
    ON validation_questions (idea_id);
