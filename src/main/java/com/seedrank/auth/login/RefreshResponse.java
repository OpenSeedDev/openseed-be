package com.seedrank.auth.login;
record RefreshResponse(String accessToken, String tokenType, long expiresIn) {}
