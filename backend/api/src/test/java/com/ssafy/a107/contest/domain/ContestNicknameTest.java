package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContestNicknameTest {

    private static final UUID OWNER = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID VIEWER = UUID.fromString("2a1d0f2e-6d63-4a1f-9d27-0a0d3f0d9a11");

    @DisplayName("다른 참가자 이름은 첫 글자와 마지막 글자만 남긴다")
    @Test
    void maskOtherParticipants() {
        assertThat(ContestNickname.visibleTo("박민용", OWNER, VIEWER)).isEqualTo("박*용");
        assertThat(ContestNickname.visibleTo("남궁민수", OWNER, VIEWER)).isEqualTo("남**수");
        assertThat(ContestNickname.visibleTo("가온", OWNER, VIEWER)).isEqualTo("가*");
        assertThat(ContestNickname.visibleTo("김", OWNER, VIEWER)).isEqualTo("*");
    }

    @DisplayName("내 제출은 이름을 그대로 보여 준다")
    @Test
    void showMyOwnName() {
        assertThat(ContestNickname.visibleTo("박민용", OWNER, OWNER)).isEqualTo("박민용");
    }
}
