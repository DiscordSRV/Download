FROM gradle:jdk11 AS build
ADD . /build
WORKDIR /build
RUN gradle build

FROM openjdk:11
WORKDIR /data
COPY --from=build /build/build/libs/DiscordSRVDownloader.jar /
CMD ["java", "-jar", "/DiscordSRVDownloader.jar"]
