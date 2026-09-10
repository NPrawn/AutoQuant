FROM golang:1.26-bookworm AS build

WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY cmd cmd
COPY internal internal

ENV CGO_ENABLED=0 \
    GOOS=linux \
    GOARCH=amd64

RUN go build -trimpath -ldflags='-s -w' -o /out/producer ./cmd/producer \
    && go build -trimpath -ldflags='-s -w' -o /out/consumer ./cmd/consumer \
    && go build -trimpath -ldflags='-s -w' -o /out/backfill ./cmd/backfill \
    && go build -trimpath -ldflags='-s -w' -o /out/healthcheck ./cmd/healthcheck

FROM gcr.io/distroless/static-debian12:nonroot

COPY --from=build /out /app

WORKDIR /app

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD ["/app/healthcheck"]

CMD ["/app/producer"]
