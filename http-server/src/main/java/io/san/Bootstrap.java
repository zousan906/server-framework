package io.san;


import io.san.server.HttpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;

import java.io.IOException;

@SpringBootApplication(scanBasePackages = {"io.san"})
public class Bootstrap implements ApplicationListener<ApplicationEvent> {
    private final HttpServer httpServer;


    public Bootstrap(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    public static void main(String[] args) throws IOException {
        SpringApplication.run(Bootstrap.class, args);
    }




    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartedEvent) {
            onApplicationStart((ApplicationStartedEvent) event);
        }
        if (event instanceof ContextClosedEvent) {
            onApplicationExitEvent((ContextClosedEvent) event);
        }
    }


    public void onApplicationExitEvent(@NonNull ContextClosedEvent event) {
        httpServer.stop();
    }

    public void onApplicationStart(@NonNull ApplicationStartedEvent event) {
        httpServer.start();
    }
}
