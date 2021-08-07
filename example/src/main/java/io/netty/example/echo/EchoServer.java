/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        // BIO NIO, BIO的区别

        // BIO也称为OIO，也就是传统的一个线程对应一个TCP链接（socket)。它是阻塞同步。如果有N个连接连到服务上，就会创建N个线程


        // NIO是多路复用，一个线程处理多个socket链接。她是非阻塞同步。
        // 在NIO中，一个线程对对应多个socket连接。NIO还分为两种，一种是传统poll（Linux）还有一种是Epoll


        // AIO它是非阻塞同步的。

        // 举个例子。
        // 吃饭场景
        // 1。排队打饭，自选模式。他需要排队，并且需要自己拿菜。BIO，他需要排队（阻塞），同时还要自己去端菜。
        // 2。点单，下单了之后，厨房做好。然后叫号让自己去端菜。NIO，他不需要排队（阻塞），但是需要自己去端菜。
        // 3。包厢模式，下单之后，厨房做好，然后服务员给你端上来。AIO，既不需要排队，也不需要自己去端菜。

        // 阻塞和同步的概念
        // 阻塞就是要排队。任务全都是当前线程处理，遇到IO，自身是等待的
        // 非阻塞就是不要排队。然后坐着等菜做好。也就是点单之后，交给其他人处理，自己坐下来歇着。IO的话，不等待，直接返回。


        // 同步与异步。要不要自己端菜，
        // 同步就是自己端菜，数据自己读取。
        // 异步就是服务员端菜。具体就是系统把数据准备好，然后回调给你

        // 对于netty来说，它是支持NIO，也支持AIO以及BIO。但是由于AIO在Linux下性能不好，所以移除了。

        // netty NIO。他的线程支持三种reactor模式。
        // 第一个的话就是单线程的reactor
        // 2，就是多线程的reactor
        // 3，主从的reactor模式，boss线程和worker线程

        // 举个例子，酒店的工作流程。
        // 单线程的reactor模式来说，就是酒店只有一个服务员，他负责所有的任务，迎宾，点菜，做菜，上菜，送客等操作。
        // 多线程的reactor模式，就是酒店多招了几个人，这些和单线程的服务员，都要走所有流程。
        // 主从的reactor模式，boss负责迎宾，然后服务员们来点菜，做菜，上菜，送客等操作。
        // socket,在这里也北成为channel。在netty就是SocketChannel
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();// 线程数量就是cpu核心*2
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    // NioServerSocketChannel 用来监听连接事件，如果有连接进来，它会需要accept一个socket连接。
                    // 也就是SocketChannel，它代表来一个服务器Ip，port && 客户端IP，port。
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .option(ChannelOption.TCP_NODELAY, false)
                    // 对于NioServerSocketChannel来说，就是处理连接，接受和断开某一台客户端的连接。
                    // handler处理连接，register
             .handler(new LoggingHandler(LogLevel.INFO))
              // SocketChannel, 处理某一台客户端的入站数据，和出站数据。handler处理数据
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     // 管道，双向链表实现的。用于对上一个handler的数据来处理。
                     // 第一个handler也就是链表的head。就是这个pipeline。
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(serverHandler);
                 }
             });

            // Start the server.启动服务，就需要绑定端口。
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
