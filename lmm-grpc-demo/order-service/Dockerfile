FROM openjdk:8-jre-alpine
COPY target/order-service.jar /home/order-service.jar
COPY docker-entrypoint.sh /home/docker-entrypoint.sh
RUN chmod +x /home/docker-entrypoint.sh
EXPOSE 9092
ENTRYPOINT ["/home/docker-entrypoint.sh"]
