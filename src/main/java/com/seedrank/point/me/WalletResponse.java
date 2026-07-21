package com.seedrank.point.me;

import java.time.Instant;

import com.seedrank.point.PointWallet;

import io.swagger.v3.oas.annotations.media.Schema;

record WalletResponse(
        @Schema(example = "300") int balance,
        @Schema(example = "0") int pendingRecoveryBalance,
        Instant updatedAt) {

    static WalletResponse from(PointWallet wallet) {
        return new WalletResponse(
                wallet.getBalance(),
                wallet.getPendingRecoveryBalance(),
                wallet.getUpdatedAt());
    }
}
