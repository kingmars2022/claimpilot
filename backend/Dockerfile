# Backend image: build with the Maven Wrapper, run on a slim JRE with Tesseract for OCR.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/claimpilot-backend-0.1.0.jar app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-fra \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --home /app claimpilot \
    && mkdir -p /app/data/uploads && chown -R claimpilot /app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER claimpilot
ENV CLAIMPILOT_OCR_LANGUAGES=eng+fra
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
