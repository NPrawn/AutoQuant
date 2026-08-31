package com.ssafy.a107.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @DisplayName("처음 저장할 때 생성 시각과 수정 시각을 같은 값으로 설정한다")
    @Test
    void setCreatedAtAndUpdatedAtOnCreate() {
        TestEntity entity = new TestEntity();

        entity.create();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @DisplayName("수정할 때 생성 시각은 유지하고 수정 시각을 갱신한다")
    @Test
    void updateOnlyUpdatedAtOnUpdate() {
        TestEntity entity = new TestEntity();
        entity.create();
        Instant createdAt = entity.getCreatedAt();

        entity.update();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    private static final class TestEntity extends BaseEntity {

        private void create() {
            onCreate();
        }

        private void update() {
            onUpdate();
        }
    }
}
