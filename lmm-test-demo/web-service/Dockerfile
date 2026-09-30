FROM openjdk:8-jre-alpine
COPY target/web-service.jar /home/web-service.jar
COPY docker-entrypoint.sh /home/docker-entrypoint.sh
RUN chmod +x /home/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/home/docker-entrypoint.sh"]
