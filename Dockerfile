FROM gradle:jdk17 AS build
ADD . /build
WORKDIR /build
RUN gradle build

FROM openjdk:17
WORKDIR /data
COPY --from=build /build/build/libs/DiscordSRVDownloader-2.jar /
CMD ["java", "-jar", "/DiscordSRVDownloader-2.jar"]
