package com.seedrank.point.me;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.point.PointLedger;
import com.seedrank.point.PointLedgerRepository;
import com.seedrank.point.PointWalletRepository;

@Service
class PointWalletQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccessTokenAuthenticator authenticator;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledgers;

    PointWalletQueryService(
            AccessTokenAuthenticator authenticator,
            PointWalletRepository wallets,
            PointLedgerRepository ledgers) {
        this.authenticator = authenticator;
        this.wallets = wallets;
        this.ledgers = ledgers;
    }

    @Transactional(readOnly = true)
    WalletResponse wallet(String authorization) {
        var principal = authenticator.authenticate(authorization);
        return wallets.findByUserId(principal.userId())
                .map(WalletResponse::from)
                .orElseThrow(() -> new IllegalStateException("wallet invariant violated"));
    }

    @Transactional(readOnly = true)
    PointLedgerPageResponse ledgers(String authorization, String encodedCursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        var principal = authenticator.authenticate(authorization);
        PointLedgerCursor cursor = PointLedgerCursor.decode(encodedCursor);
        var pageRequest = PageRequest.of(0, size + 1);
        List<PointLedger> found = cursor == null
                ? ledgers.findFirstPage(principal.userId(), pageRequest)
                : ledgers.findPageAfter(principal.userId(), cursor.createdAt(), cursor.id(), pageRequest);
        boolean hasNext = found.size() > size;
        List<PointLedger> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? new PointLedgerCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
                : null;
        return new PointLedgerPageResponse(
                page.stream().map(PointLedgerItemResponse::from).toList(),
                nextCursor,
                hasNext);
    }
}
