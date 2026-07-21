package com.seedrank.idea.draft;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/ideas")
class IdeaDraftController {
    private final IdeaDraftService service;

    IdeaDraftController(IdeaDraftService service) {
        this.service = service;
    }

    @Operation(summary = "AI 없는 아이디어 Draft 생성")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @PostMapping("/drafts")
    ResponseEntity<IdeaDraftResponse> create(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody IdeaDraftRequest request) {
        IdeaDraftResponse response = service.create(authorization, request);
        return ResponseEntity.created(URI.create("/api/v1/ideas/" + response.id())).body(response);
    }

}
