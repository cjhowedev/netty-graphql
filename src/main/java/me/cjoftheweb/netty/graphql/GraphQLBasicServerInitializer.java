package me.cjoftheweb.netty.graphql;

import graphql.GraphQL;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import java.util.function.Function;

public class GraphQLBasicServerInitializer extends ChannelInitializer<SocketChannel> {
  private static final String WEBSOCKET_PATH = "/graphql-ws";
  private static final String HTTP_PATH = "/graphql";

  private final SslContext sslCtx;
  private final Function<GraphQLRequestInfo, GraphQL> graphQLProvider;

  public GraphQLBasicServerInitializer(
      final Function<GraphQLRequestInfo, GraphQL> graphQLProvider) {
    this.sslCtx = null;
    this.graphQLProvider = graphQLProvider;
  }

  public GraphQLBasicServerInitializer(
      final Function<GraphQLRequestInfo, GraphQL> graphQLProvider, final SslContext sslCtx) {
    this.sslCtx = sslCtx;
    this.graphQLProvider = graphQLProvider;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    final ChannelPipeline pipeline = ch.pipeline();

    if (sslCtx != null) {
      pipeline.addLast(sslCtx.newHandler(ch.alloc()));
    }

    pipeline
        .addLast(new HttpServerCodec())
        .addLast(new HttpServerKeepAliveHandler())
        .addLast(new HttpObjectAggregator(65536))
        .addLast(new HttpContentCompressor())
        .addLast(new HttpContentDecompressor())
        .addLast(new WebSocketServerCompressionHandler())
        .addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true))
        .addLast(new GraphQLHTTPHandler(graphQLProvider, HTTP_PATH))
        .addLast(new GraphQLWebSocketHandler(graphQLProvider))
        .addLast(new NotFoundHandler());
  }
}
