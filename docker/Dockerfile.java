# Install environment & dependencies for rack

# FROM ubuntu:18.04

FROM openjdk:16-alpine3.13

FROM tomcat:jdk8-openjdk


WORKDIR /opt/nutshell
COPY nutshell.cnf .
COPY Nutlet.jar .
COPY nutshell /usr/local/bin
    

RUN pwd
RUN ls -ltra

# CMD ["nutshell","--help"]
CMD ["nutshell"]
