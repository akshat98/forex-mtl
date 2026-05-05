FROM eclipse-temurin:17-jdk

WORKDIR /app

RUN apt-get update \
  && apt-get install -y curl gnupg2 \
  && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" > /etc/apt/sources.list.d/sbt.list \
  && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" >> /etc/apt/sources.list.d/sbt.list \
  && curl -fsSL https://keyserver.ubuntu.com/pks/lookup?op=get\&search=0x99E82A75642AC823 | gpg --dearmor -o /etc/apt/trusted.gpg.d/sbt.gpg \
  && apt-get update \
  && apt-get install -y sbt \
  && rm -rf /var/lib/apt/lists/*

COPY project ./project
COPY build.sbt ./
RUN sbt update

COPY src ./src

EXPOSE 8080

CMD ["sbt", "run"]
