package com.seedrank.feedback.top;

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
@RequestMapping("/api/v1/contributors")
class TopContributorController {
    private final TopContributorService service;

    TopContributorController(TopContributorService service) {
        this.service = service;
    }

    @Operation(
            summary = "기여자 Top 5 조회",
            description = "인증 없이 전체 채택 기여 수 기준 상위 기여자 5명을 조회합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "기여자 Top 5 조회 성공",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = TopContributorResponse.class))))
    @GetMapping("/top")
    List<TopContributorResponse> get() {
        return service.get();
    }
}
