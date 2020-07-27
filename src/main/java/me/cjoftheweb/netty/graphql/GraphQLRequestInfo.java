package me.cjoftheweb.netty.graphql;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete;

public class GraphQLRequestInfo {
  private final HttpHeaders requestHeaders;
  private final String requestUri;

  GraphQLRequestInfo(final HandshakeComplete handshakeCompleteMessage) {
    this.requestHeaders = handshakeCompleteMessage.requestHeaders();
    this.requestUri = handshakeCompleteMessage.requestUri();
  }

  GraphQLRequestInfo(final FullHttpRequest request) {
    this.requestHeaders = request.headers();
    this.requestUri = request.uri();
  }

  public HttpHeaders getHeaders() {
    return requestHeaders;
  }

  public String getUri() {
    return requestUri;
  }
}
