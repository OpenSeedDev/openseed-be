package com.seedrank.messaging.thread;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MessageThreadRepository extends JpaRepository<MessageThread, UUID> {
    Optional<MessageThread> findByIdeaIdAndCompanyProfileIdAndAuthorId(
            UUID ideaId, UUID companyProfileId, UUID authorId);
}
