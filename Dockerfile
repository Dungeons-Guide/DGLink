FROM --platform=linux/amd64 adoptopenjdk/openjdk11-openj9
COPY kr.syeyoung.dglink-all.jar server.jar
CMD ["java","-jar","server.jar"]