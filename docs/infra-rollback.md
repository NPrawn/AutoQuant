# 운영 배포 롤백 절차

## 목적

배포 후 장애가 발생했을 때 정상 동작했던 커밋의 Docker 이미지로 서비스를 되돌린다.

이 절차는 Jenkins가 실행되는 EC2에서 수행한다. 데이터베이스와 Redis, Kafka 컨테이너는 중지하지 않고 애플리케이션 컨테이너만 이전 이미지로 재배포한다.

## 롤백 전 확인

1. 장애가 발생한 현재 이미지 태그를 확인한다.

```bash
docker inspect --format '{{.Config.Image}}' a107-backend-api
```

2. EC2에 보관된 이미지 태그를 확인한다.

```bash
docker image ls --format '{{.Repository}}:{{.Tag}}' \
  | grep -E '^a107/(backend-api|ingest|ai|frontend):' \
  | sort -V
```

3. 롤백 대상 태그가 네 개 애플리케이션 이미지에 모두 존재하는지 확인한다.

```bash
ROLLBACK_TAG=<정상_동작한_이전_태그>

for image in backend-api ingest ai frontend; do
  docker image inspect "a107/$image:$ROLLBACK_TAG" >/dev/null \
    || { echo "Missing image: a107/$image:$ROLLBACK_TAG"; exit 1; }
done
```

한 서비스의 이미지만 이전 버전으로 되돌리면 서비스 간 버전이 맞지 않을 수 있으므로, 네 개 이미지가 모두 존재하는 태그만 사용한다.

## 롤백 실행

Jenkins 사용자 권한으로 Compose를 실행한다. 운영 환경변수 파일은 Jenkins 사용자만 읽을 수 있으므로 `sudo -u jenkins`를 사용한다.

```bash
sudo -u jenkins -H bash -lc '
  set -eu
  cd /var/lib/jenkins/workspace/a107-ci_develop
  export IMAGE_PREFIX=a107
  export IMAGE_TAG=<정상_동작한_이전_태그>

  docker compose \
    --env-file /etc/a107/.env.production \
    -f infra/docker-compose.prod.yml \
    up -d --pull never --remove-orphans
'
```

`--pull never`는 롤백 중 Docker Hub에서 다른 이미지를 가져오지 않도록 한다. `docker compose down`은 사용하지 않는다.

## 롤백 후 검증

1. 모든 서비스 컨테이너 상태를 확인한다.

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

2. Frontend와 Backend의 로컬 응답을 확인한다.

```bash
curl -I http://127.0.0.1:3000
curl -i http://127.0.0.1:18080
```

Frontend는 `200`, 인증이 필요한 Backend 루트 응답은 `401`이면 정상이다.

3. PostgreSQL, Redis, Kafka 상태를 확인한다.

```bash
for container in a107-postgres a107-redis a107-kafka; do
  docker inspect --format "$container: {{.State.Status}} / {{.State.Health.Status}}" "$container"
done
```

세 컨테이너가 `running / healthy`인지 확인한다.

4. 외부 도메인으로 최종 확인한다.

```bash
curl -I https://j15a107.p.ssafy.io
curl -ki https://j15a107.p.ssafy.io/api/
```

## 롤백 후 조치

- 장애 원인과 롤백 태그를 기록한다.
- Jenkins의 해당 배포 빌드 로그를 보존한다.
- 원인 수정 후 새 커밋으로 정상 배포한다.
- 다음 Jenkins `develop` 배포가 롤백 버전을 다시 덮어쓸 수 있으므로, 장애 원인 확인 전까지 자동 배포를 실행하지 않는다.
