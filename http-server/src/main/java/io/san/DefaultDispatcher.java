package io.san;

import io.san.server.*;
import io.san.server.exception.IllegalMethodNotAllowedException;
import io.san.server.exception.IllegalPathDuplicatedException;
import io.san.server.exception.IllegalPathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class DefaultDispatcher extends AbstractDispatcher implements ApplicationContextAware {
    @Override
    protected void handle(NettyHttpRequest request, HttpResp resp)
            throws IllegalMethodNotAllowedException,
            IllegalPathNotFoundException {
        MappingHandler handler = null;
        handler = matchMappingHandler(request);
        Response response = handler.request(request);
        resp.ok(response.toJSONString());
    }


    private HashMap<Path, MappingHandler> mappingHandlers = new HashMap<>();


    private MappingHandler matchMappingHandler(NettyHttpRequest request) throws IllegalPathNotFoundException,
            IllegalMethodNotAllowedException {
        String uri = request.uri().toLowerCase();
        Map.Entry<Path, MappingHandler> targetPath = null;

        for (Map.Entry<Path, MappingHandler> pathEntry : mappingHandlers.entrySet()) {
            Path path = pathEntry.getKey();
            if (path.isEqual() ? Objects.equals(path.getUri(), uri) : uri.startsWith(path.getUri())) {
                targetPath = pathEntry;
                break;
            }
        }

        if (Objects.isNull(targetPath)) throw new IllegalPathNotFoundException();

        if (!targetPath.getKey().getMethod().equalsIgnoreCase(request.method().name())) {
            throw new IllegalMethodNotAllowedException();
        }

        return targetPath.getValue();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(RestApi.class);
        for (Map.Entry<String, Object> entry : handlers.entrySet()) {
            Object handler = entry.getValue();
            Path path = Path.make(handler.getClass().getAnnotation(RestApi.class));
            if (mappingHandlers.containsKey(path)) {
                log.error("Mapping has duplicated : {}", path.toString(), new IllegalPathDuplicatedException());
                System.exit(0);
            }

            log.info("mapping:[{}]", path);
            mappingHandlers.put(path, (MappingHandler) handler);
        }

    }
}
