package com.seedrank.idea;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select idea from Idea idea where idea.id = :id")
    Optional<Idea> findByIdForUpdate(@Param("id") UUID id);

    Optional<Idea> findByIdAndAuthorId(UUID id, UUID authorId);

    Optional<Idea> findByIdAndStatus(UUID id, IdeaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select idea from Idea idea where idea.id = :id and idea.authorId = :authorId")
    Optional<Idea> findByIdAndAuthorIdForUpdate(@Param("id") UUID id, @Param("authorId") UUID authorId);

    @Query("""
            select idea from Idea idea
            where idea.authorId = :authorId
            order by idea.updatedAt desc, idea.id desc
            """)
    List<Idea> findFirstPageByAuthor(@Param("authorId") UUID authorId, Pageable pageable);

    @Query("""
            select idea from Idea idea
            where idea.authorId = :authorId
              and (idea.updatedAt < :cursorAt
                or (idea.updatedAt = :cursorAt and idea.id < :cursorId))
            order by idea.updatedAt desc, idea.id desc
            """)
    List<Idea> findPageAfterByAuthor(
            @Param("authorId") UUID authorId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    @Query("""
            select idea from Idea idea
            where idea.authorId = :authorId and idea.status = :status
            order by idea.updatedAt desc, idea.id desc
            """)
    List<Idea> findFirstPageByAuthorAndStatus(
            @Param("authorId") UUID authorId,
            @Param("status") IdeaStatus status,
            Pageable pageable);

    @Query("""
            select idea from Idea idea
            where idea.authorId = :authorId and idea.status = :status
              and (idea.updatedAt < :cursorAt
                or (idea.updatedAt = :cursorAt and idea.id < :cursorId))
            order by idea.updatedAt desc, idea.id desc
            """)
    List<Idea> findPageAfterByAuthorAndStatus(
            @Param("authorId") UUID authorId,
            @Param("status") IdeaStatus status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
