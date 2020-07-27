package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLHTTPHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final Logger LOG = LoggerFactory.getLogger(GraphQLHTTPHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String BAD_REQUEST_MESSAGE = "Bad or unsupported GraphQL request";
  private static final String GRAPHQL_CONTENT_TYPE = "application/graphql";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";

  private final Function<GraphQLRequestInfo, GraphQL> graphQLProvider;
  private final String httpPath;

  public GraphQLHTTPHandler(
      final Function<GraphQLRequestInfo, GraphQL> graphQLProvider, final String httpPath) {
    this.graphQLProvider = graphQLProvider;
    this.httpPath = httpPath;
  }

  private String getParameter(final QueryStringDecoder uri, final String parameterName) {
    final List<String> parameters = uri.parameters().get(parameterName);
    if (parameters == null) {
      return null;
    }

    LOG.error(parameters.toString());

    return parameters.size() > 0 ? parameters.get(0) : null;
  }

  private Map<String, Object> getVariables(final String variablesJSON)
      throws JsonProcessingException {
    final Map<String, JsonNode> variableNodes = new HashMap<>();

    if (variablesJSON != null) {
      final JsonNode variablesNode = OBJECT_MAPPER.readTree(variablesJSON);
      if (variablesNode.isObject()) {
        variablesNode
            .fields()
            .forEachRemaining(
                entry -> {
                  variableNodes.put(entry.getKey(), entry.getValue());
                });
      }
    }

    final Map<String, Object> variables = new HashMap<>();
    for (Entry<String, JsonNode> entry : variableNodes.entrySet()) {
      variables.put(entry.getKey(), OBJECT_MAPPER.treeToValue(entry.getValue(), Object.class));
    }

    return variables;
  }

  private void writeBadRequest(final ChannelHandlerContext ctx, final Exception ex) {
    if (ex != null) {
      LOG.error(BAD_REQUEST_MESSAGE, ex);
    } else {
      LOG.error(BAD_REQUEST_MESSAGE);
    }
    ctx.writeAndFlush(
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
  }

  private GraphQLRequestBody getRequestBody(
      final FullHttpRequest msg, final QueryStringDecoder uri, final String requestBody)
      throws IOException {
    final GraphQLRequestBody graphQLRequestBody;
    final String query = getParameter(uri, "query");
    if (msg.method().equals(HttpMethod.GET)) {
      final String operationName = getParameter(uri, "operationName");
      final Map<String, Object> variables = getVariables(getParameter(uri, "variables"));

      graphQLRequestBody = new GraphQLRequestBody(query, operationName, variables);
    } else if (GRAPHQL_CONTENT_TYPE.equals(msg.headers().get(CONTENT_TYPE_HEADER))) {
      graphQLRequestBody = new GraphQLRequestBody(requestBody, null, null);
    } else {
      graphQLRequestBody = OBJECT_MAPPER.readValue(requestBody, GraphQLRequestBody.class);

      if (query != null) {
        graphQLRequestBody.setQuery(query);
      }
    }

    return graphQLRequestBody;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
    try {
      final QueryStringDecoder uri = new QueryStringDecoder(msg.uri());
      if (uri.path().equals(httpPath)) {
        final GraphQL graphQL = graphQLProvider.apply(new GraphQLRequestInfo(msg));
        final String requestBody = msg.content().toString(HttpConstants.DEFAULT_CHARSET);
        final GraphQLRequestBody graphQLRequestBody = getRequestBody(msg, uri, requestBody);

        if (graphQLRequestBody.getQuery() == null) {
          writeBadRequest(ctx, null);
        }

        final ExecutionResult executionResult =
            graphQL.execute(graphQLRequestBody.convertToExecutionInput());
        final GraphQLResponseBody graphQLResponseBody = new GraphQLResponseBody(executionResult);
        final String responseBody = OBJECT_MAPPER.writeValueAsString(graphQLResponseBody);
        final DefaultFullHttpResponse response =
            new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(responseBody, HttpConstants.DEFAULT_CHARSET));
        ctx.writeAndFlush(response);
      } else {
        ctx.fireChannelRead(msg);
      }
    } catch (final IOException ex) {
      writeBadRequest(ctx, ex);
    }
  }
}
