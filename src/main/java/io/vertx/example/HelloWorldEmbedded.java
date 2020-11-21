package io.vertx.example;

import io.github.tsegismont.vertx.contextual.logging.ContextualData;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorldEmbedded {

  private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldEmbedded.class);

  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();
    /*
     * Setup event bus interceptors to pass trace.id
     */
    vertx.eventBus().addOutboundInterceptor( event -> {
      String traceId = ContextualData.getOrDefault("trace.id");

      if (traceId != null) {
        event.message().headers().add("trace.id", traceId);
      }
      LOGGER.info("In outbound interceptor with trace.id: " + traceId);

      event.next();
    });
    vertx.eventBus().addInboundInterceptor(event -> {
      String traceId = event.message().headers().get("trace.id");
      if (traceId != null) {
        ContextualData.put("trace.id", traceId);
      }
      LOGGER.info("In inbound interceptor with trace.id: " + traceId);

      event.next();
    });
    vertx.eventBus().localConsumer("foo", event -> {
      LOGGER.info("Event consumer with trace.id: " + ContextualData.getOrDefault("trace.id"));
      event.reply("world");
    });

    RouterBuilder.create(vertx, "openapi.yml", event -> {
      if (event.succeeded()) {
        RouterBuilder builder = event.result();
        /*
         * Extract trace id from request headers and add it to contextual data
         * map
         */
        builder.rootHandler(rc -> {
          String traceId = rc.request().getHeader("TRACE-ID");
          if (traceId != null) {
            ContextualData.put("trace.id", traceId);
            LOGGER.info("In root handler with trace.id: " + traceId);
          }
          rc.next();
        });

        Operation operation = builder.operation("HelloWorld");
        operation.handler(event1 -> {

          LOGGER.info("Before event request with trace.id: " + ContextualData.getOrDefault("trace.id"));

          Future<Message<Object>> reply = vertx.eventBus().request("foo", "bar");
          reply.onSuccess(event2 -> {
            LOGGER.info("After event request with trace.id:" + ContextualData.getOrDefault("trace.id"));

            event1.response().end("Hello " + event2.body() + "!");
          });
        });

        Router router = builder.createRouter();

        vertx
          .createHttpServer()
          .requestHandler(router)
          .listen(8080, handler -> {
            if (handler.succeeded()) {
              System.out.println("http://localhost:8080/");
            } else {
              System.err.println("Failed to listen on port 8080");
            }
          });
      } else {
        event.cause().printStackTrace();
      }
    });
  }
}
