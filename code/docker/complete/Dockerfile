FROM jboss/base-jdk:8
MAINTAINER yourname <someone at example.com>

ADD target/lifelog-swarm.jar /opt/lifelog-swarm.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/lifelog-swarm.jar"]