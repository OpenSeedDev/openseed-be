package com.seedrank.idea.like;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdeaLikeRepository extends JpaRepository<IdeaLike, UUID> {
    boolean existsByIdeaIdAndUserId(UUID ideaId, UUID userId);

    long countByIdeaId(UUID ideaId);

    long deleteByIdeaIdAndUserId(UUID ideaId, UUID userId);
}
