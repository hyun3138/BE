# =================== 1. 빌드(Build) 스테이지 ===================
# Gradle과 JDK 17을 포함한 이미지를 기반으로 빌드 환경을 구성합니다.
FROM gradle:8.5-jdk17-alpine AS builder

# 작업 디렉터리를 생성합니다.
WORKDIR /build

# 프로젝트의 모든 파일(소스코드, 빌드 스크립트 등)을 한번에 복사합니다.
COPY . .

# 모든 파일이 복사된 상태에서 애플리케이션을 빌드합니다.
RUN gradle build --no-daemon -x test


# =================== 2. 실행(Runtime) 스테이지 ===================
# 실제 애플리케이션을 실행할 환경입니다.
# 가볍고 안정적인 Alpine Linux 기반의 이미지를 사용합니다.
FROM amazoncorretto:17-alpine

# 작업 디렉터리를 설정합니다.
WORKDIR /app

# 빌드 스테이지에서 생성된 .jar 파일을 실행 스테이지로 복사합니다.
COPY --from=builder /build/build/libs/*.jar /app/app.jar

# 컨테이너 외부에서 8080 포트로 접근할 수 있도록 노출합니다.
EXPOSE 8080

# 컨테이너가 시작될 때 실행될 명령어를 정의합니다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
