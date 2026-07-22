package com.seedrank.company.interest;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.company.profile.CompanyProfile;
import com.seedrank.company.profile.CompanyProfileRepository;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.publish.IdeaTimelineEvent;
import com.seedrank.idea.publish.IdeaTimelineEventRepository;
import com.seedrank.member.User;
import com.seedrank.messaging.thread.VerifiedCompanyRequiredException;

@Service
class CompanyInterestService {
    private final AccessTokenAuthenticator authenticator;
    private final CompanyProfileRepository companyProfiles;
    private final IdeaRepository ideas;
    private final CompanyInterestRepository interests;
    private final IdeaTimelineEventRepository timeline;
    private final Clock clock;

    CompanyInterestService(
            AccessTokenAuthenticator authenticator,
            CompanyProfileRepository companyProfiles,
            IdeaRepository ideas,
            CompanyInterestRepository interests,
            IdeaTimelineEventRepository timeline,
            Clock clock) {
        this.authenticator = authenticator;
        this.companyProfiles = companyProfiles;
        this.ideas = ideas;
        this.interests = interests;
        this.timeline = timeline;
        this.clock = clock;
    }

    @Transactional
    CompanyInterestResponse register(String authorization, UUID ideaId) {
        var principal = authenticator.authenticate(authorization);
        CompanyProfile company = verifiedCompany(principal);
        Idea idea = publishedIdeaForUpdate(ideaId);
        var existing = interests.findByIdeaIdAndCompanyProfileId(ideaId, company.getId());
        if (existing.isEmpty()) {
            var now = clock.instant();
            interests.save(CompanyInterest.create(ideaId, company, now));
            timeline.save(IdeaTimelineEvent.companyInterested(ideaId, principal.userId(), now));
        }
        return new CompanyInterestResponse(true, interests.countByIdeaId(ideaId));
    }

    @Transactional
    CompanyInterestResponse remove(String authorization, UUID ideaId) {
        var principal = authenticator.authenticate(authorization);
        CompanyProfile company = verifiedCompany(principal);
        publishedIdeaForUpdate(ideaId);
        var existing = interests.findByIdeaIdAndCompanyProfileId(ideaId, company.getId());
        if (existing.isPresent()) {
            interests.delete(existing.get());
            timeline.save(IdeaTimelineEvent.companyInterestRemoved(ideaId, principal.userId(), clock.instant()));
        }
        return new CompanyInterestResponse(false, interests.countByIdeaId(ideaId));
    }

    @Transactional(readOnly = true)
    CompanyInterestListResponse list(UUID ideaId) {
        publishedIdea(ideaId);
        var items = interests.findPublicList(ideaId).stream()
                .map(CompanyInterestItemResponse::from)
                .toList();
        return new CompanyInterestListResponse(items, items.size());
    }

    private CompanyProfile verifiedCompany(AccessTokenAuthenticator.Principal principal) {
        if (principal.role() != User.Role.COMPANY) {
            throw new VerifiedCompanyRequiredException();
        }
        return companyProfiles.findByUserIdForUpdate(principal.userId())
                .filter(profile -> profile.getVerifiedAt() != null)
                .orElseThrow(VerifiedCompanyRequiredException::new);
    }

    private Idea publishedIdeaForUpdate(UUID ideaId) {
        Idea idea = ideas.findByIdForUpdate(ideaId).orElseThrow(CompanyInterestIdeaNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) throw new CompanyInterestIdeaNotFoundException();
        return idea;
    }

    private Idea publishedIdea(UUID ideaId) {
        return ideas.findByIdAndStatus(ideaId, IdeaStatus.PUBLISHED)
                .orElseThrow(CompanyInterestIdeaNotFoundException::new);
    }
}
