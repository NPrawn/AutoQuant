# 개인 유휴 GPU를 활용한 AI Compute Sharing Platform 제안서

## 1. 프로젝트 한 줄 소개

**개인 사용자의 유휴 GPU를 Worker로 연결하여 AI 영상 업스케일링·프레임 보간 작업을 분산 처리하고, 실제 GPU 작업 기여도에 따라 제공자에게 보상을 정산하는 분산 AI Compute 플랫폼**

---

## 2. 왜 이 프로젝트를 하려는가?

AI를 이용하면 저화질 영상을 고화질로 업스케일링하거나 30fps 영상을 60fps로 보간할 수 있습니다.

하지만 AI 영상 처리는 GPU 연산량이 매우 크기 때문에 GPU 성능이 낮거나 영상 길이가 길어질수록 처리 시간이 크게 증가합니다.

반면 개인 사용자가 보유한 게이밍 PC의 GPU는 게임이나 AI 작업을 하지 않는 시간에는 대부분 유휴 상태로 남아 있습니다.

예를 들어,

```
사용자 A
RTX 4090
밤 시간 GPU 사용률 0~5%

사용자 B
RTX 4070
외출 중 GPU 사용률 0%

사용자 C
RTX 3060
웹서핑 중 GPU 사용률 5~10%
```

와 같이 사용되지 않는 GPU 자원이 존재합니다.

이러한 GPU를 하나의 Compute Pool로 연결하면 대규모 GPU 서버를 직접 구축하지 않고도 여러 사용자의 GPU를 활용해 AI 작업을 병렬 처리할 수 있습니다.

또한 GPU 제공자에게 실제 작업량에 따른 보상을 지급한다면,

> **"사용하지 않는 GPU를 다른 사람의 AI 연산에 제공하고 수익을 얻는 구조"**

를 만들 수 있습니다.

이는 비트코인 채굴처럼 GPU 자원을 통해 보상을 얻지만, 의미 없는 Hash 연산을 수행하는 대신 실제 사용자가 요청한 AI 작업을 처리한다는 차이가 있습니다.

### 해결하고 싶은 문제

> **"개인이 보유한 유휴 GPU들을 하나의 분산 Compute Pool로 구성하여 대용량 AI 작업을 더 빠르고 저렴하게 처리하고, GPU 제공자의 기여도를 측정해 공정하게 보상할 수 없을까?"**

---

## 3. 전체 동작 흐름

```
사용자 영상 업로드
        ↓
처리 옵션 선택
1080p / 4K
30fps / 60fps
        ↓
예상 처리시간 / 예상 비용 계산
        ↓
Spring Boot Control Plane
        ↓
영상 분석 및 Chunk 분할
        ↓
Task Queue 생성
        ↓
┌─────────────────────────────────────┐
│          GPU Worker Pool            │
│                                     │
│ RTX 3060      RTX 4070      RTX4090 │
│ Worker A      Worker B      Worker C│
│                                     │
│ Chunk 1       Chunk 2       Chunk 3 │
└─────────────────────────────────────┘
        ↓
Real-ESRGAN
RIFE
FFmpeg
        ↓
처리 결과 Object Storage 업로드
        ↓
Chunk 결과 병합
        ↓
최종 영상 생성
        ↓
작업 결과 검증
        ↓
GPU Worker 기여도 계산
        ↓
┌──────────────────────────────┐
│       Settlement Engine      │
│                              │
│ GPU 사용시간                 │
│ 처리 Frame                   │
│ Worker 성능                  │
│ 성공 / 실패 여부             │
└──────────────────────────────┘
        ↓
GPU 제공자 보상
        ↓
사용자 결과 영상 다운로드
```

---

## 4. 주요 기능

### AI 영상 고화질 변환

사용자가 업로드한 영상을 AI 모델을 이용해 고화질화합니다.

- 영상 업로드
- AI Super Resolution
    - 720p → 1080p
    - 1080p → 4K
- AI Frame Interpolation
    - 30fps → 60fps
- 영상 노이즈 / 압축 열화 개선
- FFmpeg 기반 Decode / Encode
- 원본 오디오 유지

영상 처리는 직접 AI 모델을 학습시키기보다 이미 검증된 pretrained 모델을 활용합니다.

```
영상
 ↓
FFmpeg Decode
 ↓
Frame
 ↓
Real-ESRGAN
 ↓
RIFE
 ↓
FFmpeg Encode
 ↓
고화질 영상
```

---

### Distributed GPU Processing

긴 영상을 여러 Chunk로 나누어 여러 GPU Worker에서 동시에 처리합니다.

```
20분 영상

Chunk 1 → RTX 4090
Chunk 2 → RTX 4070
Chunk 3 → RTX 3060
Chunk 4 → RTX 4090
Chunk 5 → RTX 4070
...
```

Worker마다 GPU 성능이 다르기 때문에 단순히 동일한 길이로 영상을 나누지 않고 Worker의 처리 성능에 따라 동적으로 작업을 할당합니다.

예를 들어,

```
RTX 4090
처리속도 120 frame/s

RTX 4070
처리속도 70 frame/s

RTX 3060
처리속도 40 frame/s
```

라면 고성능 GPU가 더 많은 Task를 가져가도록 합니다.

---

### GPU Worker Agent

GPU를 제공하려는 사용자는 자신의 PC에 Worker Agent를 설치합니다.

```
GPU Worker Agent

GPU : RTX 4070
VRAM : 12GB

GPU 공유       ON
최대 GPU 사용률 80%

공유 가능 시간
22:00 ~ 07:00

현재 작업
Task #1832

오늘 예상 보상
1,820원
```

Worker Agent는 다음 기능을 수행합니다.

- GPU 모델 탐지
- VRAM 확인
- Worker 등록
- Task 요청
- 영상 Chunk 다운로드
- AI 모델 실행
- 처리 결과 업로드
- 처리량 보고
- Heartbeat
- GPU 상태 보고

MVP에서는 별도의 복잡한 데스크톱 프로그램 대신 Python Worker를 실행 파일로 배포할 수 있습니다.

```
gpu-worker.exe

GPU detected : RTX 4070
Connecting Server...
Worker registered.
Waiting Task...
Task #381 received
Processing...
Task completed.
```

---

### Object Storage 기반 영상 전송

대용량 영상을 Spring 서버가 직접 중계하면 서버 네트워크가 병목이 될 수 있습니다. 따라서 영상 데이터는 MinIO 또는 S3를 통해 Worker가 직접 주고받도록 합니다.

```
                 Spring Boot
                      │
               Presigned URL
                      │
                      ▼
GPU Worker ←──────── S3 / MinIO
              Video Chunk
```

Spring Boot는 대용량 파일을 직접 전달하지 않고

- Job 관리
- Worker 관리
- Task Scheduling
- Presigned URL 발급

등의 Control Plane 역할에 집중합니다.

---

### Dynamic Task Scheduling

Worker마다 GPU 성능과 현재 상태가 다르기 때문에 이를 기반으로 Task를 할당합니다.

예:

```
Worker A
RTX 3060
GPU Usage 85%

Worker B
RTX 4070
GPU Usage 20%

Worker C
RTX 4090
GPU Usage 10%
```

Scheduler가

```
Task 1 → Worker C
Task 2 → Worker B
Task 3 → Worker C
```

처럼 작업을 배분합니다.

향후 다음 요소도 Scheduling 기준으로 활용할 수 있습니다.

- GPU 모델
- VRAM
- GPU 사용률
- Worker Benchmark 점수
- 네트워크 속도
- 현재 Task 수
- Task 예상 처리시간
- Worker 가격

---

### Worker Heartbeat

개인 PC는 언제든지 종료되거나 네트워크 연결이 끊길 수 있습니다. 따라서 Worker가 일정 주기로 서버에 상태를 전송합니다.

```
10:20:00 Heartbeat
10:20:05 Heartbeat
10:20:10 Heartbeat
10:20:15 없음
10:20:20 없음
10:20:25 없음

Worker Timeout
```

Heartbeat에는 다음 정보가 포함될 수 있습니다.

```
Worker ID
GPU Usage
VRAM Usage
Temperature
Current Task
Last Activity
```

---

### 장애 복구 / Retry

Worker가 작업 중 종료되더라도 전체 Job이 실패하지 않도록 합니다.

```
Worker A
Task Processing
      ↓
Heartbeat Timeout
      ↓
Worker DEAD
      ↓
Task Lease Expire
      ↓
Task RETRY
      ↓
Worker B 재할당
      ↓
Processing
      ↓
COMPLETED
```

이를 통해

- Worker 장애
- 네트워크 단절
- GPU OOM
- Worker 강제 종료

등에 대응합니다.

---

### Chunk Boundary 처리

영상은 단순히 임의의 시점에서 잘라 AI 처리하면 Chunk 경계에서 영상 품질 문제가 발생할 수 있습니다. 특히 Frame Interpolation은 앞뒤 Frame 정보를 이용하기 때문에 Chunk 간 Context가 필요합니다.

따라서 Chunk 일부를 겹치게 생성합니다.

```
Chunk A
Frame 1 ~ 105

Chunk B
Frame 96 ~ 205
        ↑
     Overlap
```

처리 후 중복 구간을 제거하여 최종 영상을 구성합니다. 이를 통해 Chunk 경계에서 발생할 수 있는

- Frame interpolation artifact
- 영상 연결 불연속
- 품질 저하

를 줄입니다.

---

### GPU 사용량 기반 과금

사용자는 AI 작업을 요청하기 전에 예상 비용을 확인합니다.

예:

```
20분 영상
1080p → 4K
30 → 60fps

예상시간
14분

예상 GPU 비용
2,300원
```

GPU 비용은 다음 정보를 활용할 수 있습니다.

```
GPU 성능
×
GPU 사용시간
×
처리 작업량
×
작업 난이도
```

예:

```
RTX 3060
10 GPU-minute

RTX 4070
6 GPU-minute

RTX 4090
4 GPU-minute
```

---

### GPU Worker 정산

작업 완료 후 Worker가 실제 수행한 연산량을 기반으로 정산합니다.

예:

```
전체 작업 비용
2,000원

Worker A 기여도
35%

Worker B 기여도
25%

Worker C 기여도
40%
```

정산:

```
Worker A     630원
Worker B     450원
Worker C     720원

플랫폼 수수료
200원
```

Worker가 Task를 실패한 경우 해당 작업은 정산 대상에서 제외하고 실제 완료한 Worker에게 보상이 지급됩니다.

---

### 비용 / 성능 기반 Scheduler

사용자가 원하는 기준에 따라 GPU Worker를 선택할 수도 있습니다.

**빠른 처리**

```
예상시간
5분

예상비용
3,000원

RTX 4090
RTX 4090
RTX 4080
```

**균형**

```
예상시간
10분

예상비용
1,800원

RTX 4070
RTX 4070
RTX 3060
```

**절약**

```
예상시간
22분

예상비용
900원

현재 유휴 GPU 활용
```

이를 통해 사용자는

> **시간과 비용 사이에서 원하는 처리 방식을 선택**

할 수 있습니다.

---

## 5. 예상 기술 스택

### Backend / Control Plane

- Java
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- SSE / WebSocket

### GPU Worker

- Python
- PyTorch
- CUDA
- NVIDIA GPU
- Worker Agent

### AI

- Real-ESRGAN
- RIFE
- Pretrained Model

### Video

- FFmpeg
- Video Chunking
- Streaming Decode / Encode

### Storage

- MinIO
- AWS S3
- Presigned URL

### Frontend

- React 또는 Next.js
- Job Dashboard
- GPU Worker Dashboard
- Processing Progress
- Before / After Video

### Infra

- Docker
- AWS / EC2
- Prometheus / Grafana 선택

---

## 6. 프로젝트의 중심은 AI 모델이 아니라 Distributed Compute + FinTech

이 프로젝트에서는 새로운 영상 AI 모델을 직접 개발하는 것이 핵심이 아닙니다. 이미 공개되어 있는 pretrained AI 모델을 실제 분산 환경에서 얼마나 안정적으로 실행할 수 있는지가 핵심입니다.

```
AI Video Pipeline
+
Distributed Worker
+
Task Scheduler
+
Fault Tolerance
+
GPU Resource Management
+
Usage Metering
+
Settlement
```

을 하나의 시스템으로 구성합니다.

따라서 단순히

> **"AI로 영상 화질을 개선했다."**

가 아니라

> **"인터넷에 분산된 개인 GPU를 하나의 Compute Pool로 구성하고 실제 AI 작업을 배분·복구·검증·정산하는 시스템을 구현했다."**

가 프로젝트의 핵심입니다.

대략적인 개발 비중은 다음과 같이 가져갈 수 있습니다.

```
Backend / Distributed System       약 50%
GPU / Video Processing             약 25%
Payment / Settlement               약 15%
AI                                 약 10%
```

---

## 7. 6인 / 5주 기준 역할

현재 팀을 **Backend 4명 + AI / GPU 2명** 정도로 가정하면 다음과 같이 나눌 수 있습니다.

### Backend

```
BE 1
Job / Task Engine
Task 상태 관리
Chunk Scheduling
Retry
Idempotency
```

```
BE 2
Worker Management
Worker Registration
Heartbeat
Worker 상태 관리
Task Lease
```

```
BE 3
GPU Usage Metering
Payment / Settlement
Worker Reward
Ledger
정합성
```

```
BE 4
Object Storage
Presigned URL
SSE 진행률
Infra
Monitoring
Performance Benchmark
```

### AI / GPU

```
AI / GPU 1
Real-ESRGAN
Video Super Resolution
FFmpeg Pipeline
Video Chunk Processing
```

```
AI / GPU 2
RIFE
Frame Interpolation
Chunk Boundary Overlap
GPU Benchmark
Worker Agent
```

Frontend는 AI 도구를 적극 활용하여 팀 전체가 필요한 화면을 구현하는 방향으로 진행합니다.

---

## 8. 6인 / 5주 기준 MVP

처음부터 실제 불특정 다수 사용자의 GPU를 연결하고 실제 현금 정산까지 구현하는 것을 목표로 하지 않습니다.

### 핵심 MVP

```
사용자 영상 업로드
↓
영상 Chunk 분할
↓
3대 이상의 GPU Worker 등록
↓
GPU 성능 기반 Task Scheduling
↓
Real-ESRGAN 처리
↓
Task 완료
↓
결과 Upload
↓
Chunk Merge
↓
최종 영상 생성
↓
Worker별 처리량 계산
↓
가상 정산
```

Worker는 팀원이 보유한 서로 다른 GPU PC를 이용해 시연할 수 있습니다.

예:

```
Worker A
RTX 3060

Worker B
RTX 4070

Worker C
RTX 4090
```

여기에 시간이 허용되면

- RIFE Frame Interpolation
- Heartbeat
- Worker 장애 자동 Retry
- GPU 가격 동적 조정
- 실제 결제 API
- GPU Worker GUI
- 작업 비용 예측
- 비용 / 성능 기반 Scheduler
- Docker 격리
- Worker 평판 시스템

등을 추가합니다.

---

## 9. 프로젝트에서 보여주고 싶은 결과

단순히

> **"AI로 영상을 4K로 만들었다."**

또는

> **"GPU 여러 개를 사용했다."**

가 아니라,

> **"성능과 안정성이 서로 다른 개인 GPU를 하나의 분산 Compute Pool로 구성하고, 작업 Scheduling·장애복구·사용량 측정·정산을 수행하는 AI Compute Marketplace의 핵심 구조를 구현했다."**

를 보여주는 것이 목표입니다.

예:

| Worker 구성 | 처리시간 | 평균 GPU 사용률 | 예상 비용 |
| --- | --- | --- | --- |
| RTX 3060 × 1 | 32분 | 94% | 1,500원 |
| RTX 3060 + 4070 | 18분 | 88% | 1,650원 |
| RTX 3060 + 4070 + 4090 | 11분 | 84% | 1,900원 |

※ 수치는 예시이며 실제 프로젝트에서는 직접 Benchmark

Worker별 결과도 측정할 수 있습니다.

| Worker | GPU | 처리 Frame | 처리시간 | 기여도 | 보상 |
| --- | --- | --- | --- | --- | --- |
| A | RTX 3060 | 8,500 | 8분 | 20% | 360원 |
| B | RTX 4070 | 14,000 | 8분 | 33% | 594원 |
| C | RTX 4090 | 20,000 | 8분 | 47% | 846원 |

추가로 다음 시스템 지표를 측정할 수 있습니다.

- Worker 수에 따른 전체 처리시간
- Worker별 처리량
- GPU 평균 사용률
- Queue 대기시간
- Chunk 크기에 따른 처리속도
- 영상 전송시간
- AI 처리시간 / 네트워크 시간 비율
- Worker 장애 감지시간
- 장애 발생 후 복구시간
- Retry 성공률
- GPU 성능별 Cost / Performance
- Worker 증가에 따른 Speedup
- Scheduling 알고리즘별 처리시간
- Storage I/O 병목
- FFmpeg Streaming 적용 전후 성능

---

## 10. 프로젝트의 장점

### 단순 AI 영상 처리 프로젝트와 차별화

Real-ESRGAN이나 RIFE를 실행하는 것 자체가 프로젝트의 핵심이 아닙니다.

핵심은

```
불특정 GPU
+
분산 작업
+
Scheduler
+
장애 대응
+
사용량 측정
+
정산
```

이라는 시스템입니다.

AI 모델을 제거하더라도 Distributed Job Processing Platform이라는 독립적인 기술 가치가 남습니다.

---

### Backend 경험을 깊게 가져갈 수 있음

단순 CRUD를 넘어

- 비동기 Job Processing
- Task Queue
- Worker Scheduling
- Distributed Worker
- Task Lease
- Heartbeat
- Retry
- Timeout
- Idempotency
- 동시성
- 장애 복구
- 대용량 파일 처리
- Object Storage
- Presigned URL
- SSE
- 사용량 Metering
- 정산
- Ledger

등을 다룰 수 있습니다.

---

### 핀테크와 기술적으로 연결 가능

단순히 결제 기능을 붙이는 것이 아니라 GPU라는 디지털 자원의 실제 사용량을 측정하여

```
사용량 측정
↓
가격 계산
↓
사용자 과금
↓
플랫폼 수수료
↓
GPU 제공자 정산
```

이라는 금융 흐름을 만들 수 있습니다.

따라서

> **Compute Resource Marketplace**

라는 형태로 핀테크 요소를 서비스 구조 자체에 포함할 수 있습니다.

---

### 결과를 수치로 검증 가능

Worker 수에 따라 실제 처리시간이 얼마나 줄어드는지 직접 확인할 수 있습니다.

```
1 Worker
31분

2 Workers
18분

3 Workers
12분
```

뿐만 아니라 비용까지 함께 비교할 수 있습니다.

```
처리시간
vs
GPU 비용
```

이라는 Trade-off를 보여줄 수 있다는 점도 장점입니다.

---

### 오픈소스 선행 사례가 존재함

유휴 컴퓨팅 자원을 활용하는 구조 자체는 이미 여러 오픈소스 프로젝트를 통해 검증되어 있습니다.

```
BOINC
개인 PC의 CPU / GPU를
분산 Computing Worker로 활용
```

```
Bacalhau
Compute Node
GPU Resource
Scheduling
Heartbeat
Orchestration
```

```
Golem
Compute Provider
Compute Requestor
Resource Marketplace
Provider Reward
```

따라서 완전히 실현 불가능한 아이디어가 아니라 기존 분산 컴퓨팅 개념을 AI 영상 처리와 GPU Marketplace에 적용하는 프로젝트입니다.

다만 해당 시스템을 그대로 사용하는 것이 아니라 구조를 참고하고,

```
Spring Boot Control Plane
+
직접 구현한 GPU Worker Agent
```

를 구현하는 것을 목표로 합니다.

---

## 11. 기존 멀티 GPU 처리와의 차별점

Real-ESRGAN 등 기존 영상 AI 도구도 하나의 서버에 여러 GPU가 존재하는 경우 Multi-GPU 처리가 가능합니다.

```
기존 구조

Server 1
GPU 0
GPU 1
GPU 2
GPU 3
```

따라서 단순히

> **"영상 Chunk를 여러 GPU에서 처리한다."**

만으로는 충분한 차별점이 되기 어렵습니다.

이 프로젝트에서 해결하려는 문제는 다른 문제입니다.

```
PC A
RTX 3060
서울

PC B
RTX 4070
부산

PC C
RTX 4090
대전

PC D
RTX 3080
언제든 종료 가능
```

처럼

- 네트워크 환경이 다르고
- GPU 성능이 다르고
- 언제 연결이 끊길지 모르며
- 서로 신뢰할 수 없는

GPU Worker들을 하나의 Compute Pool로 관리합니다.

따라서 프로젝트의 핵심 문제는

```
Multi GPU Processing
```

보다는

```
Heterogeneous Distributed GPU Computing
```

에 가깝습니다.

---

## 12. 가장 중요한 기술 검증

이 프로젝트에서 반드시 검증해야 할 부분은

> **"인터넷으로 영상을 전송하는 비용보다 GPU 분산을 통한 처리시간 감소가 실제로 더 큰가?"**

입니다.

예를 들어,

```
전체 처리시간

영상 Upload
+
Chunk Download
+
GPU Processing
+
Result Upload
+
Merge
```

가 되기 때문에 Worker 수를 계속 늘린다고 무조건 처리시간이 감소하지 않습니다.

예:

```
1 Worker
GPU 처리시간 30분
Network 2분
총 32분
```

```
3 Workers
GPU 처리시간 10분
Network 5분
총 15분
```

```
10 Workers
GPU 처리시간 4분
Network 12분
총 16분
```

처럼 어느 순간부터 네트워크와 Storage I/O가 병목이 될 가능성이 있습니다.

따라서

> **"몇 개의 Worker를 사용하는 것이 비용과 처리시간 측면에서 가장 효율적인가?"**

를 직접 Benchmark하는 것이 프로젝트의 중요한 연구 포인트가 됩니다.

---

## 13. 프로젝트 핵심 문장

> **개인이 보유한 유휴 GPU를 분산 Worker로 연결하여 대용량 AI 영상 작업을 성능에 따라 동적으로 배분하고, Worker 장애에 대응하면서 실제 GPU 연산 기여도를 측정하여 제공자에게 보상하는 분산 AI Compute 플랫폼을 구축한다.**

### 프로젝트 키워드

`Spring Boot` `Distributed Computing` `GPU Worker` `Compute Marketplace` `AI Video Processing` `Real-ESRGAN` `RIFE` `FFmpeg` `Dynamic Scheduling` `Task Queue` `Heartbeat` `Task Lease` `Retry` `Fault Tolerance` `Object Storage` `Usage Metering` `Settlement` `FinTech` `Cost Optimization` `Performance Benchmark`
