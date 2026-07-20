package com.seedrank.member.me;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/me")
class MyAccountController {
    private final MyAccountService service;

    MyAccountController(MyAccountService service) {
        this.service = service;
    }

    @Operation(summary = "내 계정 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 계정 조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @GetMapping
    MyAccountResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.get(authorization);
    }
}
