package com.seedrank.ai.job;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaDraftFactory;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.draft.IdeaDraftResponse;

@Service
class AiCandidateDraftService {
    private final AccessTokenAuthenticator authenticator;
    private final AiJobRepository jobs;
    private final AiGenerationResultRepository results;
    private final IdeaRepository ideas;
    private final IdeaDraftFactory ideaDraftFactory;
    private final JsonMapper json = JsonMapper.builder().build();

    AiCandidateDraftService(
            AccessTokenAuthenticator authenticator,
            AiJobRepository jobs,
            AiGenerationResultRepository results,
            IdeaRepository ideas,
            IdeaDraftFactory ideaDraftFactory) {
        this.authenticator = authenticator;
        this.jobs = jobs;
        this.results = results;
        this.ideas = ideas;
        this.ideaDraftFactory = ideaDraftFactory;
    }

    @Transactional
    IdeaDraftResponse create(String authorization, UUID jobId, AiCandidateDraftRequest request) {
        UUID ownerId = authenticator.authenticate(authorization).userId();
        AiJob job = jobs.findByIdForUpdate(jobId).orElseThrow(AiJobNotFoundException::new);
        if (!job.ownerId().equals(ownerId)) {
            throw new AiJobNotFoundException();
        }
        if (ideas.existsBySourceAiJobId(jobId)) {
            throw new AiJobAlreadySelectedException();
        }
        validateCandidate(job, request.candidateNumber());

        Idea idea = ideaDraftFactory.createFromAi(
                ownerId,
                jobId,
                request.candidateNumber(),
                request.title(),
                request.category(),
                request.summary(),
                request.problem(),
                request.targetCustomer(),
                request.solution(),
                request.businessModel());
        return IdeaDraftResponse.from(ideas.saveAndFlush(idea), List.of());
    }

    private void validateCandidate(AiJob job, int candidateNumber) {
        if (job.status() != AiJobStatus.SUCCEEDED) {
            throw new AiJobNotSelectableException();
        }
        String normalized = results.findByAiJobId(job.id())
                .orElseThrow(AiJobNotSelectableException::new)
                .normalizedResult();
        try {
            AiCandidateResult value = json.readValue(normalized, AiCandidateResult.class);
            if (value.candidates() == null || value.candidates().size() != 5) {
                throw new AiJobNotSelectableException();
            }
            value.candidates().get(candidateNumber - 1);
        } catch (JsonProcessingException | IndexOutOfBoundsException exception) {
            throw new AiJobNotSelectableException();
        }
    }
}
