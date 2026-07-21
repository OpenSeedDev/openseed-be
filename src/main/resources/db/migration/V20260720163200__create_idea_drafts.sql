CREATE TABLE ideas (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    summary VARCHAR(200),
    problem VARCHAR(2000) NOT NULL,
    target_customer VARCHAR(1000),
    solution VARCHAR(2000),
    business_model VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ideas_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT ck_ideas_status CHECK (status IN ('DRAFT')),
    CONSTRAINT ck_ideas_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_ideas_category_not_blank CHECK (btrim(category) <> ''),
    CONSTRAINT ck_ideas_problem_not_blank CHECK (btrim(problem) <> '')
);

CREATE INDEX idx_ideas_author_status_updated_at
    ON ideas (author_id, status, updated_at DESC, id DESC);
