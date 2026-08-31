package com.ssafy.a107.member.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "member",
        schema = "account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_member_login_provider_identity",
                        columnNames = {"login_provider", "login_provider_member_id"}
                )
        }
)
public class Member extends BaseEntity {

    private static final int MAX_LOGIN_PROVIDER_MEMBER_ID_LENGTH = 255;
    private static final int MAX_EMAIL_ADDRESS_LENGTH = 320;
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_provider", nullable = false, length = 30)
    private LoginProvider loginProvider;

    @Column(name = "login_provider_member_id", nullable = false, length = MAX_LOGIN_PROVIDER_MEMBER_ID_LENGTH)
    private String loginProviderMemberId;

    @Column(name = "email_address", nullable = false, unique = true, length = MAX_EMAIL_ADDRESS_LENGTH)
    private String emailAddress;

    @Column(name = "display_name", nullable = false, length = MAX_DISPLAY_NAME_LENGTH)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false, length = 20)
    private MemberStatus memberStatus;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Member() {
    }

    public Member(
            LoginProvider loginProvider,
            String loginProviderMemberId,
            String emailAddress,
            String displayName
    ) {
        if (loginProvider == null) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_LOGIN_IDENTITY);
        }
        this.loginProvider = loginProvider;
        this.loginProviderMemberId = requireNonBlank(
                loginProviderMemberId,
                MAX_LOGIN_PROVIDER_MEMBER_ID_LENGTH,
                ApiErrorCode.MEMBER_INVALID_LOGIN_IDENTITY
        );
        this.emailAddress = normalizeEmailAddress(emailAddress);
        this.displayName = requireNonBlank(
                displayName,
                MAX_DISPLAY_NAME_LENGTH,
                ApiErrorCode.MEMBER_INVALID_PROFILE
        );
        this.memberStatus = MemberStatus.ACTIVE;
    }

    public void changeEmailAddress(String emailAddress) {
        ensureNotWithdrawn();
        this.emailAddress = normalizeEmailAddress(emailAddress);
    }

    public void changeDisplayName(String displayName) {
        ensureNotWithdrawn();
        this.displayName = requireNonBlank(
                displayName,
                MAX_DISPLAY_NAME_LENGTH,
                ApiErrorCode.MEMBER_INVALID_PROFILE
        );
    }

    public void recordSuccessfulLogin(Instant loggedInAt) {
        if (memberStatus != MemberStatus.ACTIVE || loggedInAt == null) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION);
        }
        this.lastLoginAt = loggedInAt;
    }

    public void suspend() {
        if (memberStatus != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION);
        }
        memberStatus = MemberStatus.SUSPENDED;
    }

    public void reactivate() {
        if (memberStatus != MemberStatus.SUSPENDED) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION);
        }
        memberStatus = MemberStatus.ACTIVE;
    }

    public void withdraw(Instant withdrawalTime) {
        ensureNotWithdrawn();
        if (withdrawalTime == null) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION);
        }
        memberStatus = MemberStatus.WITHDRAWN;
        withdrawnAt = withdrawalTime;
    }

    private void ensureNotWithdrawn() {
        if (memberStatus == MemberStatus.WITHDRAWN) {
            throw new ApiException(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION);
        }
    }

    private static String normalizeEmailAddress(String emailAddress) {
        return requireNonBlank(
                emailAddress,
                MAX_EMAIL_ADDRESS_LENGTH,
                ApiErrorCode.MEMBER_INVALID_PROFILE
        ).toLowerCase(Locale.ROOT);
    }

    private static String requireNonBlank(String value, int maximumLength, ApiErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new ApiException(errorCode);
        }

        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw new ApiException(errorCode);
        }
        return normalizedValue;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public LoginProvider getLoginProvider() {
        return loginProvider;
    }

    public String getLoginProviderMemberId() {
        return loginProviderMemberId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MemberStatus getMemberStatus() {
        return memberStatus;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
