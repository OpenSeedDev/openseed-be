package com.seedrank.ranking.main;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/rankings")
class RankingMainController {
    private final RankingMainService service;

    RankingMainController(RankingMainService service) {
        this.service = service;
    }

    @Operation(
            summary = "랭킹순 메인 카드 조회",
            description = "인증과 공개 범위 구분 없이 현재 랭킹을 순위대로 조회합니다. 정렬과 필터는 제공하지 않습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "현재 랭킹 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RankingCardResponse.class))))
    @GetMapping
    List<RankingCardResponse> get() {
        return service.get();
    }
}
