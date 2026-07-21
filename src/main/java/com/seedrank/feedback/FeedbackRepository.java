package com.seedrank.feedback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    @Query("""
            select feedback from Feedback feedback
            join fetch feedback.author
            where feedback.idea.id = :ideaId and feedback.deletedAt is null
            order by case when feedback.acceptedAt is not null then 0 else 1 end,
                     feedback.createdAt desc, feedback.id desc
            """)
    List<Feedback> findFirstPage(@Param("ideaId") UUID ideaId, Pageable pageable);

    @Query("""
            select feedback from Feedback feedback
            join fetch feedback.author
            where feedback.idea.id = :ideaId and feedback.deletedAt is null
              and ((feedback.acceptedAt is not null
                    and (feedback.createdAt < :cursorAt
                         or (feedback.createdAt = :cursorAt and feedback.id < :cursorId)))
                   or feedback.acceptedAt is null)
            order by case when feedback.acceptedAt is not null then 0 else 1 end,
                     feedback.createdAt desc, feedback.id desc
            """)
    List<Feedback> findPageAfterAccepted(
            @Param("ideaId") UUID ideaId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    @Query("""
            select feedback from Feedback feedback
            join fetch feedback.author
            where feedback.idea.id = :ideaId
              and feedback.deletedAt is null
              and feedback.acceptedAt is null
              and (feedback.createdAt < :cursorAt
                   or (feedback.createdAt = :cursorAt and feedback.id < :cursorId))
            order by feedback.createdAt desc, feedback.id desc
            """)
    List<Feedback> findPageAfterUnaccepted(
            @Param("ideaId") UUID ideaId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
