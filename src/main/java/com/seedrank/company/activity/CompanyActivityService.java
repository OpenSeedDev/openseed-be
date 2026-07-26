package com.seedrank.company.activity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.company.profile.CompanyProfile;
import com.seedrank.company.profile.CompanyProfileRepository;
import com.seedrank.member.User;
import com.seedrank.messaging.thread.VerifiedCompanyRequiredException;

@Service
class CompanyActivityService {
    private final AccessTokenAuthenticator authenticator;
    private final CompanyProfileRepository companyProfiles;
    private final CompanyActivityQueryRepository activities;

    CompanyActivityService(
            AccessTokenAuthenticator authenticator,
            CompanyProfileRepository companyProfiles,
            CompanyActivityQueryRepository activities) {
        this.authenticator = authenticator;
        this.companyProfiles = companyProfiles;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    CompanyActivityResponse get(String authorization) {
        var principal = authenticator.authenticate(authorization);
        CompanyProfile company = verifiedCompany(principal);
        return new CompanyActivityResponse(
                activities.findInterests(company.getId()),
                activities.findInquiries(company.getId()));
    }

    private CompanyProfile verifiedCompany(AccessTokenAuthenticator.Principal principal) {
        if (principal.role() != User.Role.COMPANY) {
            throw new VerifiedCompanyRequiredException();
        }
        return companyProfiles.findByUserId(principal.userId())
                .filter(profile -> profile.getVerifiedAt() != null)
                .orElseThrow(VerifiedCompanyRequiredException::new);
    }
}
