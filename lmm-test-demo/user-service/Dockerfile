FROM openjdk:8-jre-alpine
COPY target/user-service.jar /home/user-service.jar
COPY docker-entrypoint.sh /home/docker-entrypoint.sh
RUN chmod +x /home/docker-entrypoint.sh
EXPOSE 8081
ENTRYPOINT ["/home/docker-entrypoint.sh"]
