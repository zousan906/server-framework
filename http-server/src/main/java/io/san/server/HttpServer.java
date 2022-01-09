package io.san.server;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class HttpServer {
    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);


    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder {
        private int maxConnect = -1;
        private int backlog;
        private int acceptorNum = 1;
        private int keepaliveTime = 60000;
        private int maxRequest = 1000;
        private AbstractDispatcher dispatcher;
        private int ioNum = Runtime.getRuntime().availableProcessors() * 2;
        private int port = 8080;
        private String host = "0.0.0.0";
        private int idleTime = 60;

        public Builder() {
        }

        public Builder dispatcher(AbstractDispatcher dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }

        public Builder maxRequest(int maxRequest) {
            this.maxRequest = maxRequest;
            return this;
        }

        public Builder maxConnect(int maxConnect) {
            this.maxConnect = maxConnect;
            return this;
        }

        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        public Builder acceptorNum(int acceptorNum) {
            this.acceptorNum = acceptorNum;
            return this;
        }

        public Builder ioNum(int ioNum) {
            this.ioNum = ioNum;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder keepaliveTime(int keepaliveTime) {
            this.keepaliveTime = keepaliveTime;
            return this;
        }

        public Builder idleTime(int idleTime) {
            this.idleTime = idleTime;
            return this;
        }

        public HttpServer build() {
            Preconditions.checkNotNull(this.dispatcher, "Dispatcher can not be null");
            return new HttpServer(keepaliveTime, maxConnect, backlog, acceptorNum, ioNum,
                    port, host, maxRequest, this.idleTime, dispatcher);
        }

    }


    @ChannelHandler.Sharable
    private class ConnectLimitHandler extends ChannelInboundHandlerAdapter {

        private final ConnectLimiter limiter;

        private ConnectLimitHandler(int maxConnect) {
            this.limiter = new ConnectLimiter(maxConnect);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel channel = (Channel) msg;
            boolean permits = this.limiter.tryAcquire(10, TimeUnit.MILLISECONDS);
            if (!permits) {
                // 直接关闭,避免 time_wait
                channel.config().setOption(ChannelOption.SO_LINGER, 0);
                channel.unsafe().closeForcibly();
                return;
            }
            Session session = new Session(maxRequest, keepaliveTime);
            session.setLimit(limiter);
            Attribute<Session> attr = channel.attr(ChannelKey.CONNECT_SESSION);
            attr.setIfAbsent(session);
            super.channelRead(ctx, msg);
        }
    }

    @ChannelHandler.Sharable
    private class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {


        private AbstractDispatcher dispatcher;

        public HttpServerHandler(AbstractDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }


        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                if (log.isDebugEnabled()) {
                    log.debug("Channel Expired; closed:{}", ctx.channel());
                }
                ctx.close();
            }

            super.userEventTriggered(ctx, evt);


        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);

            log.error("new active:{}", ctx.channel());
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            super.channelRegistered(ctx);
            log.error("new channelRegistered:{}", ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            log.error("new channelInactive:{}", ctx.channel());

        }


        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            super.channelUnregistered(ctx);
            log.error("new channelUnregistered:{}", ctx.channel());
            // 主动断开 被动断开 都会激活
            releaseSession(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest fullRequest) {

            Attribute<Session> sessionAttr = context.channel().attr(ChannelKey.CONNECT_SESSION);
            Session session = sessionAttr.get();
            session.incrementReq();
            FullHttpRequest copy = fullRequest.copy();
            try {
                NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(copy);
                FullHttpResponse response = this.dispatcher.handleRequest(nettyHttpRequest);
                if (session.isKeepalive(nettyHttpRequest.keepalive())) {
                    context.writeAndFlush(response);
                } else {
                    response.headers().set("Connection", "close");
                    context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            } finally {
                ReferenceCountUtil.release(copy);
            }


        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            releaseSession(ctx);
        }


        private void releaseSession(ChannelHandlerContext ctx) {
            if (ctx.channel().hasAttr(ChannelKey.CONNECT_SESSION)) {
                Attribute<Session> attr = ctx.channel().attr(ChannelKey.CONNECT_SESSION);
                Session session = attr.get();
                if (Objects.nonNull(session)) {
                    session.clearLimitRef();
                    attr.set(null);
                }
            }
        }
    }

    private ChannelFuture channelFuture;
    private EventLoopGroup acceptorGroup;
    private EventLoopGroup ioGroup;

    private final String host;
    private final int port;
    private final int ioNum;
    private final int backlog;
    private final int acceptorNum;
    private final int keepaliveTime;
    private final int maxConnect;
    private final int maxRequest;
    private final int idleTime;
    private final AbstractDispatcher dispatcher;

    private HttpServer(int keepaliveTime, int maxConnect, int backlog, int acceptorNum,
                       int ioNum, int port, String host, int maxRequest, int idleTime,
                       AbstractDispatcher dispatcher) {
        this.keepaliveTime = keepaliveTime;
        this.maxConnect = maxConnect;
        this.backlog = backlog;
        this.acceptorNum = acceptorNum;
        this.ioNum = ioNum;
        this.port = port;
        this.host = host;
        this.idleTime = idleTime;
        this.maxRequest = maxRequest;
        this.dispatcher = dispatcher;
    }

    public void start() {

        ServerBootstrap bootstrap = new ServerBootstrap();
        this.acceptorGroup = new NioEventLoopGroup(this.acceptorNum, new ThreadFactoryBuilder()
                .setNameFormat("http-accept-%d").build());
        this.ioGroup = new NioEventLoopGroup(this.ioNum, new ThreadFactoryBuilder()
                .setNameFormat("http-io-%d").build());

        bootstrap.group(this.acceptorGroup, this.ioGroup);
        bootstrap.channel(SystemUtil.isLinux() ? EpollServerSocketChannel.class : NioServerSocketChannel.class);

        bootstrap.handler(new ConnectLimitHandler(this.maxConnect));
        if (this.backlog > 0) {
            bootstrap.option(NioChannelOption.SO_BACKLOG, this.backlog);
        }
        bootstrap.childOption(NioChannelOption.TCP_NODELAY, true);
        bootstrap.childOption(NioChannelOption.SO_REUSEADDR, true);
        bootstrap.childOption(NioChannelOption.SO_KEEPALIVE, true);
        bootstrap.childOption(NioChannelOption.SO_RCVBUF, 2048);
        bootstrap.childOption(NioChannelOption.SO_SNDBUF, 2048);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                // 不能用这个 太多的定时任务 TODO
//                ch.pipeline().addLast("idle", new IdleStateHandler(idleTime, 0, 0, TimeUnit.SECONDS));
                ch.pipeline().addLast("codec", new HttpServerCodec());
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(512 * 1024));
                ch.pipeline().addLast("bizHandler", new HttpServerHandler(dispatcher));
            }
        });
        channelFuture = bootstrap.bind(this.host, this.port).syncUninterruptibly()
                .addListener(future -> {
                    log.info("Netty Http Server started on port {}", this.port);
                });

        channelFuture.channel().closeFuture().addListener(future -> {
            log.info("Netty Http Server Start Shutdown");
            acceptorGroup.shutdownGracefully();
            ioGroup.shutdownGracefully();
        });

    }

    public void stop() {
        if (Objects.nonNull(channelFuture)) {
            channelFuture.channel().close();
        }
    }
}
