package com.seedrank.point.me;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/me")
class PointWalletQueryController {
    private final PointWalletQueryService service;

    PointWalletQueryController(PointWalletQueryService service) {
        this.service = service;
    }

    @Operation(summary = "내 Point 지갑 조회", description = "현재 잔액과 회수 가능 대기 잔액을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지갑 조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @GetMapping("/wallet")
    WalletResponse wallet(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.wallet(authorization);
    }

    @Operation(summary = "내 Point 원장 조회", description = "Append-only Point 원장을 최신순 Cursor로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "원장 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 Cursor 또는 페이지 크기"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @GetMapping("/point-ledgers")
    PointLedgerPageResponse ledgers(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "이전 응답의 nextCursor")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return service.ledgers(authorization, cursor, size);
    }
}
