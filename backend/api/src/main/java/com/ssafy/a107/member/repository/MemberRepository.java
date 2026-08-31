package com.ssafy.a107.member.repository;

import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByLoginProviderAndLoginProviderMemberId(
            LoginProvider loginProvider,
            String loginProviderMemberId
    );
}
