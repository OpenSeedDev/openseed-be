package com.seedrank.company.interest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CompanyInterestRepository extends JpaRepository<CompanyInterest, UUID> {
    Optional<CompanyInterest> findByIdeaIdAndCompanyProfileId(UUID ideaId, UUID companyProfileId);

    long countByIdeaId(UUID ideaId);

    @Query("""
            select interest from CompanyInterest interest
            join fetch interest.companyProfile
            where interest.ideaId = :ideaId
            order by interest.interestedAt desc, interest.id desc
            """)
    List<CompanyInterest> findPublicList(@Param("ideaId") UUID ideaId);
}
