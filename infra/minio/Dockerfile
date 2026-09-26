FROM golang:1.24-alpine AS build
RUN apk add --no-cache git
RUN git clone --depth 1 --branch RELEASE.2025-09-06T17-38-46Z https://github.com/minio/minio.git /src
WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /minio .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates \
    && addgroup -S -g 10001 minio \
    && adduser -S -u 10001 -G minio minio \
    && mkdir /data \
    && chown minio:minio /data
COPY --from=build /minio /usr/local/bin/minio
USER 10001:10001
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
