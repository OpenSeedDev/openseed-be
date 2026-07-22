package com.seedrank.unit.recovery;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/unit-lots/{lotId}/recover")
class SeedUnitRecoveryController {
    private final SeedUnitRecoveryService service;

    SeedUnitRecoveryController(SeedUnitRecoveryService service) { this.service = service; }

    @Operation(summary = "Seed Unit Lot 전체 회수", description = "24시간 잠금이 끝난 소유 Lot 전체를 현재가로 회수합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체 Lot 회수 성공 또는 같은 결과 재조회"),
            @ApiResponse(responseCode = "400", description = "부분 회수 요청"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "소유한 Lot 없음"),
            @ApiResponse(responseCode = "409", description = "24시간 잠금 미해제")
    })
    @PostMapping
    SeedUnitRecoveryResponse recover(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID lotId,
            HttpServletRequest request) {
        if (request.getContentLengthLong() > 0) throw new PartialRecoveryNotSupportedException();
        return service.recover(authorization, lotId);
    }
}
