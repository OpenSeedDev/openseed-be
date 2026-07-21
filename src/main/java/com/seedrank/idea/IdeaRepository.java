package com.seedrank.idea;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    Optional<Idea> findByIdAndAuthorId(UUID id, UUID authorId);
}
