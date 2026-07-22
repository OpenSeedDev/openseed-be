package com.seedrank.messaging.thread;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MessageThreadRepository extends JpaRepository<MessageThread, UUID> {
    Optional<MessageThread> findByIdeaIdAndCompanyProfileIdAndAuthorId(
            UUID ideaId, UUID companyProfileId, UUID authorId);

    @Query("""
            select thread from MessageThread thread
            where thread.id = :threadId
              and (thread.authorId = :userId
                   or thread.companyProfileId in (
                       select profile.id from CompanyProfile profile where profile.user.id = :userId))
            """)
    Optional<MessageThread> findParticipantThread(
            @Param("threadId") UUID threadId,
            @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select thread from MessageThread thread
            where thread.id = :threadId
              and (thread.authorId = :userId
                   or thread.companyProfileId in (
                       select profile.id from CompanyProfile profile where profile.user.id = :userId))
            """)
    Optional<MessageThread> findParticipantThreadForUpdate(
            @Param("threadId") UUID threadId,
            @Param("userId") UUID userId);
}
