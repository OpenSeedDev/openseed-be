package com.seedrank.auth.login;
import java.util.UUID;
import com.seedrank.member.User;
record LoginResponse(String accessToken, String tokenType, long expiresIn, UUID userId, User.Role role) {}
