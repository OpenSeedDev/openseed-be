package com.seedrank.idea.publish;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdeaVersionRepository extends JpaRepository<IdeaVersion, UUID> {
}
