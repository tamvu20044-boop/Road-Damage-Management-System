# MinIO Community Edition is distributed as source. Build the server binary
# during the Compose build instead of depending on the retired Docker Hub image.
FROM golang:latest AS build
# The runtime image is Alpine (musl), so compile without a glibc dependency.
RUN CGO_ENABLED=0 go install github.com/minio/minio@latest

FROM alpine:3.22
RUN apk add --no-cache ca-certificates
COPY --from=build /go/bin/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
