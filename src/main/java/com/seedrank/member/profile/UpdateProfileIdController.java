package com.seedrank.member.profile;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seedrank.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "My Account")
@RestController
@RequestMapping("/api/v1/me/profile-id")
class UpdateProfileIdController {

    private final UpdateProfileIdService service;

    UpdateProfileIdController(UpdateProfileIdService service) {
        this.service = service;
    }

    @Operation(
            summary = "공개 프로필 아이디 수정",
            description = "중복과 변경 횟수를 제한하지 않으며 영문자·숫자·밑줄 3~20자 및 예약어 정책을 적용합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "사용할 수 없는 프로필 아이디",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping
    UpdateProfileIdResponse update(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) UpdateProfileIdRequest request) {
        return service.update(authorization, request);
    }
}
