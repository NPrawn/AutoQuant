package com.ssafy.a107.auth.jwt;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
