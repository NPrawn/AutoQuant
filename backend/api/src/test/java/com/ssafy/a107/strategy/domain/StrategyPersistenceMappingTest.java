package com.ssafy.a107.strategy.domain;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyPersistenceMappingTest {

    @DisplayName("Strategy DSL은 PostgreSQL JSONB 컬럼으로 매핑한다")
    @Test
    void mapStrategyDslToJsonbColumn() throws NoSuchFieldException {
        Field strategyDslField = Strategy.class.getDeclaredField("strategyDsl");
        Column column = strategyDslField.getAnnotation(Column.class);
        JdbcTypeCode jdbcTypeCode = strategyDslField.getAnnotation(JdbcTypeCode.class);

        assertThat(strategyDslField.getType()).isEqualTo(StrategyDsl.class);
        assertThat(column.name()).isEqualTo("strategy_dsl");
        assertThat(column.columnDefinition()).isEqualTo("jsonb");
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    @DisplayName("Strategy는 Member를 지연 로딩 다대일 관계로 매핑한다")
    @Test
    void mapMemberAsLazyManyToOne() throws NoSuchFieldException {
        Field memberField = Strategy.class.getDeclaredField("member");
        ManyToOne manyToOne = memberField.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = memberField.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(joinColumn.name()).isEqualTo("member_id");
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo("fk_strategy_member");
    }

    @DisplayName("Strategy 테이블과 활성 전략 조회 Repository 계약을 정의한다")
    @Test
    void mapTableAndDeclareActiveStrategyQueries() throws NoSuchMethodException {
        Table table = Strategy.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("strategy");
        assertThat(table.name()).isEqualTo("strategy");
        assertThat(StrategyRepository.class
                .getMethod("findAllByMemberMemberIdAndDeletedAtIsNull", UUID.class)
                .getReturnType())
                .isEqualTo(List.class);
        assertThat(StrategyRepository.class
                .getMethod("findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull", Long.class, UUID.class)
                .getReturnType())
                .isEqualTo(Optional.class);
    }
}
