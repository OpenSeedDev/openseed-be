package com.seedrank.idea.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ValidationQuestionRepository extends JpaRepository<ValidationQuestion, UUID> {

    List<ValidationQuestion> findByIdeaIdOrderBySortOrder(UUID ideaId);

    @Modifying
    @Query("delete from ValidationQuestion question where question.ideaId = :ideaId")
    void deleteAllByIdeaId(UUID ideaId);
}
