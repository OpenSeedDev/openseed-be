package com.seedrank.auth.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;

@RestController @RequestMapping("/api/v1/auth")
class RefreshController {
    private final RefreshService service; private final boolean secure;
    RefreshController(RefreshService service, @Value("${app.auth.cookie-secure:true}") boolean secure) { this.service=service; this.secure=secure; }
    @Operation(summary="인증 갱신") @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="401")})
    @PostMapping("/refresh") ResponseEntity<RefreshResponse> refresh(@CookieValue(name="refresh_token", required=false) String token) {
        var result=service.refresh(token);
        var cookie=ResponseCookie.from("refresh_token", result.refreshToken()).httpOnly(true).secure(secure).sameSite("Lax")
                .path("/api/v1/auth").maxAge(14*24*60*60L).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
    }
}
