package com.seedrank.idea.publish;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdeaVersionRepository extends JpaRepository<IdeaVersion, UUID> {
    @Query("select coalesce(max(version.versionNumber), 0) from IdeaVersion version where version.ideaId = :ideaId")
    int findMaxVersionNumber(@Param("ideaId") UUID ideaId);
}
