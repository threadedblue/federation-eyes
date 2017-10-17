FROM openjdk:alpine
#FROM java:openjdk-8-jdk
ENV C2WTROOT=/home/cpswt
ADD target/federation.manager-0.3.0-SNAPSHOT.jar /home
ADD federation.fed /home
ADD script.xml /home
ADD nar /home/nar/
ADD fedmgr.yml /home
ADD example.keystore /home
RUN mkdir -p /home/cpswt/log
WORKDIR /home
CMD ["java", "-jar", "-Djava.library.path=nar/processid-0.3.0-SNAPSHOT-amd64-Linux-gpp-jni/lib/amd64-Linux-gpp/jni/", "-Djava.net.preferIPv4Stack=true", "federation.manager-0.3.0-SNAPSHOT.jar", "server", "fedmgr.yml"]
EXPOSE 8888