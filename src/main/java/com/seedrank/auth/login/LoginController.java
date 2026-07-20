package com.seedrank.auth.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController @RequestMapping("/api/v1/auth")
class LoginController {
    private final LoginService service; private final boolean secure;
    LoginController(LoginService service, @Value("${app.auth.cookie-secure:true}") boolean secure) { this.service=service; this.secure=secure; }
    @Operation(summary="로그인")
    @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400"),@ApiResponse(responseCode="401")})
    @PostMapping("/login") ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        var result=service.login(request);
        var cookie=ResponseCookie.from("refresh_token", result.refreshToken()).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/api/v1/auth").maxAge(14*24*60*60L).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
    }
}
