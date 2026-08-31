# JPA 컨벤션

## 유니크 제약조건

- 단일 컬럼 UK는 해당 필드의 `@Column(unique = true)`로 선언한다.
- 복합 UK는 엔티티의 `@Table(uniqueConstraints = @UniqueConstraint(...))`로 선언한다.
- 운영 DB의 마이그레이션·DDL에도 엔티티와 동일한 유니크 제약조건을 반영한다. 제약 이름을 명시적으로 관리해야 하면 단일 컬럼도 `@Table` 선언을 허용한다.
