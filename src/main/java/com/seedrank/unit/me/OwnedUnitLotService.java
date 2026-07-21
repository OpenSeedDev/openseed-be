package com.seedrank.unit.me;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.unit.SeedUnitLot;
import com.seedrank.unit.SeedUnitLotRepository;

@Service
class OwnedUnitLotService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String NON_MONETARY_NOTICE =
            "Seed Unit은 현금, 지분, 배당 또는 수익권이 아니며 현금화할 수 없습니다.";

    private final AccessTokenAuthenticator authenticator;
    private final SeedUnitLotRepository lots;
    private final Clock clock;

    OwnedUnitLotService(AccessTokenAuthenticator authenticator, SeedUnitLotRepository lots, Clock clock) {
        this.authenticator = authenticator;
        this.lots = lots;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    OwnedUnitLotPageResponse get(String authorization, String encodedCursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        UUID userId = authenticator.authenticate(authorization).userId();
        OwnedUnitLotCursor cursor = OwnedUnitLotCursor.decode(encodedCursor);
        var pageRequest = PageRequest.of(0, size + 1);
        List<SeedUnitLot> found = cursor == null
                ? lots.findFirstOwnedPage(userId, SeedUnitLot.Status.LOCKED, pageRequest)
                : lots.findOwnedPageAfter(
                        userId, SeedUnitLot.Status.LOCKED, cursor.purchasedAt(), cursor.id(), pageRequest);
        boolean hasNext = found.size() > size;
        List<SeedUnitLot> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? new OwnedUnitLotCursor(page.getLast().purchasedAt(), page.getLast().id()).encode()
                : null;
        Map<UUID, Integer> activeUnits = lots.findActiveUnitTotals(userId, SeedUnitLot.Status.LOCKED).stream()
                .collect(Collectors.toMap(
                        SeedUnitLotRepository.ActiveUnitTotal::getIdeaId,
                        total -> Math.toIntExact(total.getUnits())));
        var now = clock.instant();
        return new OwnedUnitLotPageResponse(
                page.stream().map(lot -> OwnedUnitLotItemResponse.from(
                        lot, activeUnits.getOrDefault(lot.ideaId(), 0), now)).toList(),
                nextCursor,
                hasNext,
                NON_MONETARY_NOTICE);
    }
}
