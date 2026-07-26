package com.seedrank.search;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seedrank.common.error.ApiError;
import com.seedrank.ranking.main.RankingCardResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/search")
class IdeaSearchController {
    private final IdeaSearchService service;

    IdeaSearchController(IdeaSearchService service) {
        this.service = service;
    }

    @Operation(
            summary = "랭킹 아이디어 제목·키워드 검색",
            description = "최대 20자의 검색어로 현재 랭킹의 제목과 키워드를 검색합니다. "
                    + "공개 범위와 관계없이 안전한 랭킹 카드 7개 필드만 반환합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "검색 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RankingCardResponse.class))))
    @ApiResponse(
            responseCode = "400",
            description = "검색어 누락·공백 또는 20자 초과",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping
    List<RankingCardResponse> search(
            @Parameter(
                    required = true,
                    description = "제목·키워드 부분 일치 검색어",
                    schema = @Schema(minLength = 1, maxLength = 20))
            @RequestParam(required = false) String q) {
        return service.search(q);
    }
}
