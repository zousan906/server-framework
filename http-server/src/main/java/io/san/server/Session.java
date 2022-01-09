package io.san.server;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Session {
    private final AtomicInteger requestCount;
    private long lastReqTime;
    private final long createTime;
    private final int maxRequest;
    private final int keepaliveTime;
    private ConnectLimiter semaphore;

    public Session(int maxRequest, int keepaliveTime) {
        this.createTime = System.currentTimeMillis();
        this.requestCount = new AtomicInteger(0);
        this.maxRequest = maxRequest;
        this.keepaliveTime = keepaliveTime;
    }


    public Session setLimit(ConnectLimiter semaphore) {
        this.semaphore = semaphore;
        return this;
    }

    public Session clearLimitRef() {
        if (Objects.nonNull(this.semaphore)) {
            this.semaphore.release();
            this.semaphore = null;
        }
        return this;
    }

    public int incrementReq() {
        this.lastReqTime = System.currentTimeMillis();
        return this.requestCount.incrementAndGet();
    }


    public boolean isKeepalive(boolean keepalive) {

        return keepalive ?
                requestCount.get() < maxRequest && (System.currentTimeMillis() - createTime) < keepaliveTime
                : false;
    }
}
