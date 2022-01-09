package io.san.server;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

public class HttpResp {
    HttpResponseStatus status;
    HttpHeaders headers;
    String content;


    public HttpHeaders headers() {
        return this.headers;
    }


    public void ok(){
        this.status = HttpResponseStatus.OK;
    }

    public void ok(String content){
        this.status = HttpResponseStatus.OK;
        this.content = content;
    }
}
