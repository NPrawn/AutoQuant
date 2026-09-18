package com.ssafy.a107.contest.domain;

import java.util.UUID;

/*
 * 순위표에 보이는 참가자 이름이다. 회원 이름은 SSAFY 로그인이 준 실명이라 다른 참가자에게는 가려서 보여 준다.
 * 첫 글자와 마지막 글자만 남기고, 두 글자면 뒷글자를 가린다. 내 제출은 그대로 보여 준다.
 */
public final class ContestNickname {

    private static final String MASK = "*";

    private ContestNickname() {
    }

    public static String visibleTo(String displayName, UUID ownerId, UUID viewerId) {
        return ownerId.equals(viewerId) ? displayName : mask(displayName);
    }

    static String mask(String displayName) {
        int[] letters = displayName.codePoints().toArray();
        if (letters.length <= 1) {
            return MASK;
        }
        String first = new String(letters, 0, 1);
        if (letters.length == 2) {
            return first + MASK;
        }
        return first + MASK.repeat(letters.length - 2) + new String(letters, letters.length - 1, 1);
    }
}
