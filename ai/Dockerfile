FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app app

RUN groupadd --system app \
    && useradd --system --gid app --create-home app \
    && chown -R app:app /app

USER app

EXPOSE 8000

# 헬스체크는 LLM 을 부르지 않는다. /health 는 프로세스 생존만 본다 —
# 과금 요인이 헬스체크에 붙으면 컨테이너가 살아 있는 동안 계속 크레딧을 쓴다.
# slim 이미지에 curl 이 없어 표준 라이브러리로 친다.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3     CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health').read()"]

# 컨테이너 안에서만 0.0.0.0 이다. 컨테이너 인터페이스에 바인딩하지 않으면 Spring 이
# 부르지 못한다. 호스트로 포트를 여는 것은 compose 가 정하는 별개 문제다.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
