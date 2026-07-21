package com.seedrank.idea.publish;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaVisibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idea_versions")
class IdeaVersion {
    @Id private UUID id;
    @Column(name = "idea_id", nullable = false) private UUID ideaId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 50) private String category;
    @Column(nullable = false, length = 200) private String summary;
    @Column(nullable = false, length = 2000) private String problem;
    @Column(name = "target_customer", nullable = false, length = 1000) private String targetCustomer;
    @Column(nullable = false, length = 2000) private String solution;
    @Column(name = "business_model", nullable = false, length = 2000) private String businessModel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IdeaVisibility visibility;
    @Column(name = "validation_questions", nullable = false, columnDefinition = "TEXT") private String validationQuestions;
    @Column(name = "editor_id", nullable = false) private UUID editorId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IdeaVersion() {}

    static IdeaVersion first(Idea idea, List<String> questions, Instant now) {
        IdeaVersion version = new IdeaVersion();
        version.id = UUID.randomUUID();
        version.ideaId = idea.id();
        version.versionNumber = 1;
        version.title = idea.title();
        version.category = idea.category();
        version.summary = idea.summary();
        version.problem = idea.problem();
        version.targetCustomer = idea.targetCustomer();
        version.solution = idea.solution();
        version.businessModel = idea.businessModel();
        version.visibility = idea.visibility();
        version.validationQuestions = String.join("\n", questions);
        version.editorId = idea.authorId();
        version.createdAt = now;
        return version;
    }
}
