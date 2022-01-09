package io.san;

import io.san.server.AbstractDispatcher;
import io.san.server.HttpServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpServerConfiguration {

    private final AbstractDispatcher dispatcher;

    public HttpServerConfiguration(AbstractDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }


    @Bean
    public HttpServer httpServer() {
        HttpServer build = HttpServer.builder()
                .keepaliveTime(60000)
                .maxRequest(5)
                .maxConnect(1)
                .idleTime(10)
                .dispatcher(dispatcher).build();
        return build;
    }
}
