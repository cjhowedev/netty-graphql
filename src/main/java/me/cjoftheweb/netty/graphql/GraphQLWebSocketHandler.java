package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
  private static final Logger LOG = LoggerFactory.getLogger(GraphQLWebSocketHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int DEFAULT_TICKS_PER_WHEEL = 4096;
  private static final long HEARTBEAT_TIMEOUT = 5000L;
  private static final String BAD_REQUEST_MESSAGE = "Bad or unsupported GraphQL frame";

  private final Function<GraphQLRequestInfo, GraphQL> graphQLProvider;
  private final Timer keepAliveTimer;
  private final ConcurrentHashMap<String, GraphQLConnectionMetadata> connectionMetadataMap =
      new ConcurrentHashMap<>();

  public GraphQLWebSocketHandler(final Function<GraphQLRequestInfo, GraphQL> graphQLProvider) {
    this.graphQLProvider = graphQLProvider;
    this.keepAliveTimer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, DEFAULT_TICKS_PER_WHEEL);
  }

  public GraphQLWebSocketHandler(
      final Function<GraphQLRequestInfo, GraphQL> graphQLProvider, final Timer keepAliveTimer) {
    this.graphQLProvider = graphQLProvider;
    this.keepAliveTimer = keepAliveTimer;
  }

  @Override
  public void userEventTriggered(final ChannelHandlerContext ctx, final Object msg)
      throws Exception {
    super.userEventTriggered(ctx, msg);
    if (msg instanceof HandshakeComplete) {
      final HandshakeComplete handshakeCompleteMessage = (HandshakeComplete) msg;
      final GraphQL graphQL =
          graphQLProvider.apply(new GraphQLRequestInfo(handshakeCompleteMessage));
      getConnectionMetadata(ctx).setGraphQL(graphQL);
    }
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final WebSocketFrame msg)
      throws Exception {
    if (msg instanceof TextWebSocketFrame) {
      final String jsonFrame =
          ((TextWebSocketFrame) msg).content().toString(HttpConstants.DEFAULT_CHARSET);

      processWebsocketFrame(ctx, readFrame(ctx, jsonFrame));
    }
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
    super.channelInactive(ctx);
    removeConnectionMetadata(ctx).ifPresent(m -> m.close());
  }

  private GraphQLWebSocketFrame readFrame(final ChannelHandlerContext ctx, final String jsonFrame) {
    try {
      return OBJECT_MAPPER.readValue(jsonFrame, GraphQLWebSocketFrame.class);
    } catch (final IOException ex) {
      writeParseError(ctx, ex);
      return null;
    }
  }

  private void processWebsocketFrame(
      final ChannelHandlerContext ctx, final GraphQLWebSocketFrame webSocketFrame) {
    if (webSocketFrame == null) {
      return;
    }

    final GraphQLConnectionMetadata connectionMetadata = getConnectionMetadata(ctx);
    if (GraphQLConstants.GQL_CONNECTION_INIT.equals(webSocketFrame.getType())) {
      writeConnectionAck(ctx);
      writeKeepAlive(ctx);
      scheduleKeepAlive(ctx);
    } else if (GraphQLConstants.GQL_START.equals(webSocketFrame.getType())) {
      final GraphQLRequestBody requestBody =
          OBJECT_MAPPER.convertValue(webSocketFrame.getPayload(), GraphQLRequestBody.class);

      if (requestBody.getQuery() == null) {
        writeBadRequest(ctx, webSocketFrame, new InvalidParameterException("A query is required"));
      } else {
        final ExecutionResult executionResult =
            connectionMetadata.getGraphQL().execute(requestBody.convertToExecutionInput());
        final GraphQLResponseSubscriber subscriber =
            new GraphQLResponseSubscriber(ctx, webSocketFrame.getId());
        try {
          final Publisher<ExecutionResult> executionResults = executionResult.getData();
          connectionMetadata.putSubscriber(webSocketFrame.getId(), subscriber);
          executionResults.subscribe(subscriber);
        } catch (final ClassCastException ex) {
          subscriber.writeExecutionResult(executionResult);
        }
      }
    } else if (GraphQLConstants.GQL_STOP.equals(webSocketFrame.getType())) {
      connectionMetadata.removeSubscriber(webSocketFrame.getId()).ifPresent(s -> s.cancel());
    } else if (GraphQLConstants.GQL_CONNECTION_TERMINATE.equals(webSocketFrame.getType())) {
      removeConnectionMetadata(ctx).ifPresent(m -> m.close());
      ctx.channel().close();
    }
  }

  private static String getChannelID(final ChannelHandlerContext ctx) {
    return ctx.channel().id().asLongText();
  }

  private GraphQLConnectionMetadata getConnectionMetadata(final ChannelHandlerContext ctx) {
    final String channelID = getChannelID(ctx);
    connectionMetadataMap.putIfAbsent(channelID, new GraphQLConnectionMetadata(channelID));
    return connectionMetadataMap.get(channelID);
  }

  private Optional<GraphQLConnectionMetadata> removeConnectionMetadata(
      final ChannelHandlerContext ctx) {
    final String channelID = getChannelID(ctx);
    return Optional.ofNullable(connectionMetadataMap.remove(channelID));
  }

  private boolean hasConnectionMetadata(final ChannelHandlerContext ctx) {
    final String channelID = getChannelID(ctx);
    return connectionMetadataMap.containsKey(channelID);
  }

  private void scheduleKeepAlive(final ChannelHandlerContext ctx) {
    final GraphQLConnectionMetadata connectionMetadata = getConnectionMetadata(ctx);
    if (connectionMetadata.getKeepAliveTimeout() != null) {
      return;
    }

    connectionMetadata.setKeepAliveTimeout(
        keepAliveTimer.newTimeout(
            new TimerTask() {
              @Override
              public void run(final Timeout timeout) {
                writeKeepAlive(ctx);
                if (hasConnectionMetadata(ctx)) {
                  connectionMetadata.setKeepAliveTimeout(null);
                  scheduleKeepAlive(ctx);
                }
              }
            },
            HEARTBEAT_TIMEOUT,
            TimeUnit.MILLISECONDS));
  }

  private void writeConnectionAck(final ChannelHandlerContext ctx) {
    writeResponse(ctx, new GraphQLWebSocketFrame(GraphQLConstants.GQL_CONNECTION_ACK));
  }

  private void writeKeepAlive(final ChannelHandlerContext ctx) {
    writeResponse(ctx, new GraphQLWebSocketFrame(GraphQLConstants.GQL_CONNECTION_KEEP_ALIVE));
  }

  private void writeBadRequest(
      final ChannelHandlerContext ctx, final GraphQLWebSocketFrame request, final Exception ex) {
    if (ex != null) {
      LOG.error(BAD_REQUEST_MESSAGE, ex);
    } else {
      LOG.error(BAD_REQUEST_MESSAGE);
    }
    final GraphQLWebSocketFrame responseFrame =
        new GraphQLWebSocketFrame(
            ex.getLocalizedMessage(), request.getId(), GraphQLConstants.GQL_ERROR);
    writeResponse(ctx, responseFrame);
  }

  private void writeParseError(final ChannelHandlerContext ctx, final Exception ex) {
    if (ex != null) {
      LOG.error(BAD_REQUEST_MESSAGE, ex);
    } else {
      LOG.error(BAD_REQUEST_MESSAGE);
    }
    final GraphQLWebSocketFrame responseFrame =
        new GraphQLWebSocketFrame(
            ex.getLocalizedMessage(), null, GraphQLConstants.GQL_CONNECTION_ERROR);
    writeResponse(ctx, responseFrame);
  }

  private void writeResponse(
      final ChannelHandlerContext ctx, final GraphQLWebSocketFrame webSocketFrame) {
    try {
      final String textResponseFrame = OBJECT_MAPPER.writeValueAsString(webSocketFrame);
      ctx.channel().writeAndFlush(new TextWebSocketFrame(textResponseFrame));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
