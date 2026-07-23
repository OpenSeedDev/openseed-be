package com.seedrank.unit.recovery.payout;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/me/pending-recovery/payout")
class PendingRecoveryPayoutController {
    private final PendingRecoveryPayoutService service;

    PendingRecoveryPayoutController(PendingRecoveryPayoutService service) { this.service = service; }

    @Operation(
            summary = "회수 가능 대기 잔액 수동 지급",
            description = "고정된 회수 대기 잔액을 일일 회수 한도와 지갑 여유분 안에서 지급합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지급 성공 또는 현재 지급 가능액 없음"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @PostMapping
    PendingRecoveryPayoutResponse payout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.payout(authorization);
    }
}
