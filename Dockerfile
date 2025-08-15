# Dockerfile

# 베이스 이미지로 Java 17 사용
FROM openjdk:17-jdk-slim

# ✅ JAR 파일이 위치할 경로를 Loark 디렉터리 내부로 수정합니다.
ARG JAR_FILE=Loark/build/libs/*.jar

# 위 경로의 JAR 파일을 app.jar로 복사
COPY ${JAR_FILE} app.jar

# 애플리케이션 실행
ENTRYPOINT ["java","-jar","/app.jar"]