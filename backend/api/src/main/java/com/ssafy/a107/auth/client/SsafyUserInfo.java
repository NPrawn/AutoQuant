package com.ssafy.a107.auth.client;

/* SSAFY 응답 DTO 를 도메인까지 흘리지 않기 위해 경계에서 이 타입으로 바꾼다. */
public record SsafyUserInfo(String userId, String email, String name) {
}
