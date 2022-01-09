package io.san.server;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.san.server.exception.IllegalMethodNotAllowedException;
import io.san.server.exception.IllegalPathNotFoundException;

public abstract class AbstractDispatcher {


    FullHttpResponse handleRequest(NettyHttpRequest fullRequest) {
        NettyHttpRequest request = new NettyHttpRequest(fullRequest);
        HttpResp resp = new HttpResp();
        FullHttpResponse response = null;
        try {
            handle(request, resp);
            response = NettyHttpResponse.make(resp);
        } catch (IllegalMethodNotAllowedException error) {
            response = NettyHttpResponse.make(HttpResponseStatus.METHOD_NOT_ALLOWED);
        } catch (IllegalPathNotFoundException error) {
            response = NettyHttpResponse.make(HttpResponseStatus.NOT_FOUND);
        } catch (Exception e) {
            response = NettyHttpResponse.makeError(e);
        }
        return response;
    }


    protected abstract void handle(NettyHttpRequest request, HttpResp resp) throws IllegalMethodNotAllowedException, IllegalPathNotFoundException;
}
