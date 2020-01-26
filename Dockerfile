FROM alpine:3.7
RUN apk add --update openjdk8-jre-base nss
COPY build/install/newsroom/ /app/
WORKDIR /app
CMD ["java", "-cp", "lib/*", "io.github.gjum.discord.newsroom.MainKt"]
