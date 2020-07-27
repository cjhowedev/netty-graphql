package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLResponseSubscriber implements Subscriber<ExecutionResult> {
  private static final Logger LOG = LoggerFactory.getLogger(GraphQLWebSocketHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
  private final ChannelHandlerContext ctx;
  private final String requestId;

  public GraphQLResponseSubscriber(final ChannelHandlerContext ctx, final String requestId) {
    this.ctx = ctx;
    this.requestId = requestId;
  }

  public void cancel() {
    subscriptionRef.get().cancel();
  }

  @Override
  public void onSubscribe(final Subscription s) {
    subscriptionRef.set(s);
    s.request(1);
  }

  @Override
  public void onNext(final ExecutionResult er) {
    writeExecutionResult(er);
    subscriptionRef.get().request(1);
  }

  @Override
  public void onError(Throwable t) {
    LOG.error("Error in response to GraphQL request " + requestId, t);
    writeResponse(new GraphQLWebSocketFrame(t.getMessage(), requestId, GraphQLConstants.GQL_ERROR));
  }

  @Override
  public void onComplete() {
    writeResponse(new GraphQLWebSocketFrame(null, requestId, GraphQLConstants.GQL_COMPLETE));
  }

  public void writeExecutionResult(final ExecutionResult er) {
    final GraphQLResponseBody response = new GraphQLResponseBody(er);
    writeResponse(new GraphQLWebSocketFrame(response, requestId, GraphQLConstants.GQL_DATA));
  }

  private void writeResponse(final GraphQLWebSocketFrame webSocketFrame) {
    try {
      final String textResponseFrame = OBJECT_MAPPER.writeValueAsString(webSocketFrame);
      ctx.channel().writeAndFlush(new TextWebSocketFrame(textResponseFrame));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
