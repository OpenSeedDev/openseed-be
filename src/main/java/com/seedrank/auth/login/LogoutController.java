package com.seedrank.auth.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/v1/auth")
class LogoutController {
    private final LogoutService service;
    private final boolean secure;

    LogoutController(LogoutService service, @Value("${app.auth.cookie-secure:true}") boolean secure) {
        this.service = service;
        this.secure = secure;
    }

    @Operation(summary = "현재 세션 로그아웃")
    @ApiResponses(@ApiResponse(responseCode = "204"))
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        service.logoutCurrent(refreshToken);
        return noContentWithDeletedCookie();
    }

    @Operation(summary = "모든 세션 로그아웃")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@ApiResponse(responseCode = "204"), @ApiResponse(responseCode = "401")})
    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        service.logoutAll(authorization);
        return noContentWithDeletedCookie();
    }

    private ResponseEntity<Void> noContentWithDeletedCookie() {
        var cookie = ResponseCookie.from("refresh_token", "").httpOnly(true).secure(secure)
                .sameSite("Lax").path("/api/v1/auth").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
}
