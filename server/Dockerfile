FROM gradle:7.6.1-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build
EXPOSE 8000
RUN chmod a+rx build/libs/someJar-1.0-SNAPSHOT.jar
COPY build/tmp/genJar/MANIFEST.MF build/libs 
RUN ls build/libs

CMD ["java", "-jar", "build/libs/all-in-one-jar-1.0-SNAPSHOT.jar"]