package com.seedrank.unit.purchase;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ideas/{ideaId}")
class SeedUnitPurchaseController {
    private final SeedUnitPurchaseService service;

    SeedUnitPurchaseController(SeedUnitPurchaseService service) {
        this.service = service;
    }

    @Operation(summary = "Seed Unit 구매 미리보기")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 가격과 예상 결제 결과"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "본인 아이디어 구매"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어 없음"),
            @ApiResponse(responseCode = "409", description = "구매 한도 초과")
    })
    @PostMapping("/unit-purchase-preview")
    SeedUnitPreviewResponse preview(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId,
            @Valid @RequestBody SeedUnitPreviewRequest request) {
        return service.preview(authorization, ideaId, request);
    }

    @Operation(summary = "Seed Unit 구매")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Point 차감과 잠금 Lot 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "본인 아이디어 구매"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어 없음"),
            @ApiResponse(responseCode = "409", description = "가격 변경·잔액 부족·구매 한도 초과")
    })
    @PostMapping("/unit-purchases")
    @ResponseStatus(HttpStatus.CREATED)
    SeedUnitPurchaseResponse purchase(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId,
            @Valid @RequestBody SeedUnitPurchaseRequest request) {
        return service.purchase(authorization, ideaId, request);
    }
}
