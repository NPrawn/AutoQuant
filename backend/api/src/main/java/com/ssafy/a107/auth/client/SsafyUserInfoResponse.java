package com.ssafy.a107.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/* userId 는 항상 제공되고, email 과 name 은 사용자가 필수 동의를 한 경우에만 내려온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyUserInfoResponse(String userId, String email, String name) {
}
