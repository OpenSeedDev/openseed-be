CREATE TABLE message_thread_messages (
    id UUID PRIMARY KEY,
    thread_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_message_thread_messages_thread FOREIGN KEY (thread_id)
        REFERENCES message_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_thread_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id),
    CONSTRAINT ck_message_thread_messages_content
        CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_message_thread_messages_thread_sent
    ON message_thread_messages (thread_id, sent_at ASC, id ASC);
