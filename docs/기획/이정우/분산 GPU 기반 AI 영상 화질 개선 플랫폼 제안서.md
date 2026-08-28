## 1. 프로젝트 한 줄 소개

**AI 영상 업스케일링·프레임 보간 작업을 여러 GPU Worker에 분산 처리하여, 긴 영상 처리 시간을 줄이는 Spring Boot 기반 영상 처리 플랫폼**

---

## 2. 왜 이 프로젝트를 하려는가?

AI를 활용하면 저화질 영상을 고화질로 업스케일링하거나, 30fps 영상을 60fps로 보간할 수 있습니다.

하지만 영상 AI 처리는 연산량이 매우 크기 때문에 GPU 성능이 낮거나 영상 길이가 길어질수록 처리 시간이 크게 증가합니다.

예를 들어 10~30분짜리 영상을 고화질·고FPS로 변환하면 한 대의 GPU에서는 처리 시간이 수십 분 이상 걸릴 수 있습니다.

### 해결하고 싶은 문제

> **“한 대의 GPU에서 오래 걸리는 영상 AI 처리를 여러 GPU에 분산하면 처리 시간을 얼마나 줄일 수 있을까?”**
>

영상을 여러 구간(Chunk)으로 나눈 뒤 여러 GPU Worker에 동시에 배분하여 병렬 처리하고, 완료된 결과를 다시 하나의 영상으로 합치는 구조를 구현합니다.

---

## 3. 전체 동작 흐름

```
사용자 영상 업로드
        ↓
처리 옵션 선택
(1080p / 4K, 30 / 60fps)
        ↓
Spring Boot 서버
        ↓
영상 분석 및 Chunk 분할
        ↓
작업 Queue 등록
        ↓
┌────────────┬────────────┬────────────┐
│ GPU Worker │ GPU Worker │ GPU Worker │
│     A      │     B      │     C      │
│  Chunk 1   │  Chunk 2   │  Chunk 3   │
└────────────┴────────────┴────────────┘
        ↓
AI 영상 처리
Upscaling + Frame Interpolation
        ↓
처리 결과 병합
        ↓
최종 영상 생성
        ↓
사용자 다운로드
```

---

## 4. 주요 기능

### 🎥 영상 처리

- 영상 업로드
- AI 영상 업스케일링
    - 720p → 1080p
    - 1080p → 4K
- AI 프레임 보간
    - 30fps → 60fps
- FFmpeg 기반 영상 분할 / 병합
- 원본 오디오 유지

### ⚡ 분산 처리

- 영상을 Chunk 단위로 분할
- 여러 GPU Worker에 작업 분배
- 처리 완료 Worker가 다음 작업을 가져가는 동적 작업 처리
- 서로 다른 성능의 GPU Worker 사용 가능

### 🖥️ 백엔드

- 영상 처리 Job 생성 및 상태 관리
- Job / Task / Worker 상태 DB 관리
- 비동기 작업 Queue
- 실시간 진행률 제공
- Worker 상태 확인
- 실패 작업 Retry

### 🛡️ 장애 대응

GPU Worker가 처리 도중 종료되더라도

```
PROCESSING
    ↓
Worker 응답 없음
    ↓
Timeout
    ↓
RETRY
    ↓
다른 Worker에서 재처리
```

할 수 있도록 구성합니다.

---

## 5. 예상 기술 스택

### Backend

- Java
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis 또는 Kafka
- SSE

### AI / GPU Worker

- Python
- PyTorch
- Real-ESRGAN
- RIFE
- CUDA

### Video

- FFmpeg

### Frontend

- React 또는 Next.js

### Infra

- Docker
- MinIO 또는 S3

---

## 6. 프로젝트의 중심은 AI보다는 Backend

AI 모델을 직접 학습시키는 프로젝트가 아니라 **이미 검증된 AI 모델을 실제 서비스에서 어떻게 빠르고 안정적으로 처리할 것인지**가 핵심입니다.

대략적인 개발 비중은 다음과 같이 생각하고 있습니다.

```
Java Backend / 분산 시스템   약 60%
영상 / GPU Pipeline          약 25%
AI                           약 15%
```

따라서 Spring Backend 관점에서도 다음과 같은 경험을 할 수 있습니다.

- 비동기 Job Processing
- Message Queue
- 분산 Worker 관리
- Task Scheduling
- 동시성 제어
- Retry / Timeout
- Heartbeat
- 장애 복구
- 대용량 파일 처리
- 실시간 진행률 전송
- 성능 테스트

---

## 7. 6인 / 5주 기준 목표

처음부터 완성형 AI 영상 편집 프로그램을 만드는 것은 목표로 하지 않습니다.

### 핵심 MVP

```
영상 업로드
↓
영상 Chunk 분할
↓
여러 GPU Worker에 분산
↓
AI Upscaling
↓
Frame Interpolation
↓
영상 Merge
↓
결과 다운로드
```

여기에 시간이 허용되면

- GPU Worker 동적 스케줄링
- Worker Heartbeat
- 장애 발생 시 자동 Retry
- SSE 실시간 진행률
- Before / After 비교 UI

등을 추가합니다.

---

## 8. 프로젝트에서 보여주고 싶은 결과

단순히

> “AI로 영상 화질을 개선했다”
>

가 아니라,

> **“여러 GPU Worker를 이용한 분산 처리로 영상 처리 시간을 실제로 얼마나 단축했는가”**
>

를 수치로 보여주는 것이 목표입니다.

예시:

| GPU Worker 수 | 처리 시간 |
| --- | --- |
| 1 Worker | 30분 |
| 2 Workers | 17분 |
| 3 Workers | 12분 |

※ 실제 결과는 프로젝트에서 직접 Benchmark

추가로 다음 지표도 비교할 수 있습니다.

- 전체 영상 처리 시간
- Worker별 처리량
- GPU 사용률
- Queue 대기 시간
- 장애 발생 후 복구 시간
- Worker 증가에 따른 성능 향상률

---

## 9. 프로젝트의 장점

### ✅ 결과물이 직관적임

Before / After 영상으로 결과를 바로 보여줄 수 있어 데모 효과가 좋습니다.

### ✅ 일반적인 CRUD 프로젝트와 차별화

단순 게시판이나 쇼핑몰이 아니라 **대용량 비동기 작업과 분산 처리**라는 명확한 기술적 문제가 존재합니다.

### ✅ Java Backend 경험을 깊게 가져갈 수 있음

Spring Boot가 GPU Worker를 관리하고 작업을 스케줄링하는 구조이기 때문에 Backend 비중을 크게 가져갈 수 있습니다.

### ✅ 성능 개선을 직접 증명할 수 있음

Worker 수에 따른 처리시간을 직접 측정해 프로젝트 성과를 수치로 보여줄 수 있습니다.

---

## 10. 프로젝트 핵심 문장

> **대용량 AI 영상 처리 과정에서 발생하는 높은 GPU 처리 지연을 해결하기 위해, Spring Boot 기반 분산 Job Processing 시스템을 설계하고 영상 Chunk 분할·GPU Worker 분산 처리·장애 복구를 구현한다.**
>

### 프로젝트 키워드

`Spring Boot` `Distributed Processing` `GPU Worker` `Async Job` `Kafka/Redis` `FFmpeg` `AI Video Enhancement` `Real-ESRGAN` `RIFE` `Fault Tolerance` `Performance Benchmark`