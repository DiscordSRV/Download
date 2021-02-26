FROM gradle:jdk11-openj9 AS build
ADD . /build
WORKDIR /build
RUN gradle build

FROM openjdk:11
WORKDIR /bot
COPY --from=build /build/build/libs/DiscordSRVDownloader.jar /bot
CMD ["java", "-jar", "DiscordSRVDownloader.jar"]
