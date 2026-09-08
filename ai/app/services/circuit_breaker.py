"""게이트웨이가 죽었을 때 계속 두드리지 않게 막는 서킷 브레이커.

분당 호출 상한(`MAX_LLM_CALLS_PER_MINUTE`)과 목적이 다르다. 그쪽은 크레딧을 지키고
이쪽은 장애를 격리한다. 요청이 분당 다섯 건이라 상한에 안 걸려도, 게이트웨이가 죽어
있으면 매 건이 타임아웃까지 기다린다. 죽은 것을 아는데 기다리는 시간이 이 모듈이
없애려는 비용이다.
"""

from time import perf_counter

from app.core.errors import LLMCircuitOpenError


# 연속 실패 몇 번에 열지, 얼마나 닫아 둘지. 환경변수로 빼지 않는다 —
# 운영 중에 바꿀 값이 아니고 os.environ 을 읽는 파일은 config.py 하나로 둔다.
CONSECUTIVE_FAILURES_TO_OPEN = 5
OPEN_SECONDS = 30.0


class CircuitBreaker:
    """닫힘 → 열림 → 반열림을 오가는 최소 구현.

    시간 소스는 `perf_counter()` 다. 호출 상한이 쓰는 것과 같은 소스라야 테스트에서
    시간을 다루는 방법이 하나로 통일된다. 벽시계를 섞지 않는다.
    """

    def __init__(
        self,
        failure_threshold: int = CONSECUTIVE_FAILURES_TO_OPEN,
        open_seconds: float = OPEN_SECONDS,
    ) -> None:
        self._failure_threshold = failure_threshold
        self._open_seconds = open_seconds
        self._consecutive_failures = 0
        self._opened_at: float | None = None

    def before_call(self) -> None:
        """열려 있으면 호출하지 않고 즉시 막는다. 차단 시간이 지났으면 한 번 흘려보낸다."""
        if self._opened_at is None:
            return
        if perf_counter() - self._opened_at < self._open_seconds:
            raise LLMCircuitOpenError()
        # 반열림. 이 한 건의 결과가 다시 열지 닫을지를 정한다.
        self._opened_at = None

    def record_success(self) -> None:
        self._consecutive_failures = 0
        self._opened_at = None

    def record_failure(self) -> None:
        """게이트웨이 장애만 넣는다. 우리 쪽 문제인 검증 실패와 크레딧 상한은 넣지 않는다."""
        self._consecutive_failures += 1
        if self._consecutive_failures >= self._failure_threshold:
            self._opened_at = perf_counter()

    def reset(self) -> None:
        """테스트가 프로세스 전역 상태를 되돌릴 때 쓴다."""
        self._consecutive_failures = 0
        self._opened_at = None


# ponytail: 프로세스 하나 기준이다. 워커를 여러 개 띄우면 워커마다 따로 센다.
# 프로세스 간 공유는 배포 구간에서 본다.
llm_circuit = CircuitBreaker()
