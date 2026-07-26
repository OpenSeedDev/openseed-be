package com.seedrank.company.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
class CompanyActivityQueryRepository {
    private final EntityManager entityManager;

    CompanyActivityQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    List<CompanyInterestActivityResponse> findInterests(UUID companyProfileId) {
        return entityManager.createQuery("""
                        select new com.seedrank.company.activity.CompanyInterestActivityResponse(
                            idea.id, idea.title, interest.interestedAt)
                        from CompanyInterest interest
                        join Idea idea on idea.id = interest.ideaId
                        where interest.companyProfile.id = :companyProfileId
                        order by interest.interestedAt desc, interest.id desc
                        """, CompanyInterestActivityResponse.class)
                .setParameter("companyProfileId", companyProfileId)
                .getResultList();
    }

    List<CompanyInquiryActivityResponse> findInquiries(UUID companyProfileId) {
        return entityManager.createQuery("""
                        select new com.seedrank.company.activity.CompanyInquiryActivityResponse(
                            thread.id, idea.id, idea.title, thread.updatedAt)
                        from MessageThread thread
                        join Idea idea on idea.id = thread.ideaId
                        where thread.companyProfileId = :companyProfileId
                        order by thread.updatedAt desc, thread.id desc
                        """, CompanyInquiryActivityResponse.class)
                .setParameter("companyProfileId", companyProfileId)
                .getResultList();
    }
}
