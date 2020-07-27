package me.cjoftheweb.netty.graphql;

import graphql.GraphQL;
import io.netty.util.Timeout;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLConnectionMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(GraphQLWebSocketHandler.class);

  private final String channelID;
  private GraphQL graphQL = null;
  private Map<String, GraphQLResponseSubscriber> subscriberMap = new ConcurrentHashMap<>();
  private Timeout keepAliveTimeout = null;

  public GraphQLConnectionMetadata(final String channelID) {
    this.channelID = channelID;
  }

  public String getChannelID() {
    return this.channelID;
  }

  public GraphQL getGraphQL() {
    return this.graphQL;
  }

  public void setGraphQL(final GraphQL graphQL) {
    this.graphQL = graphQL;
  }

  public Map<String, GraphQLResponseSubscriber> getSubscriberMap() {
    return this.subscriberMap;
  }

  public void setSubscriberMap(final Map<String, GraphQLResponseSubscriber> subscriberMap) {
    this.subscriberMap = subscriberMap;
  }

  public void putSubscriber(final String requestID, final GraphQLResponseSubscriber subscriber) {
    this.subscriberMap.put(requestID, subscriber);
  }

  public Optional<GraphQLResponseSubscriber> removeSubscriber(final String requestID) {
    return Optional.ofNullable(getSubscriberMap().remove(requestID));
  }

  public Timeout getKeepAliveTimeout() {
    return this.keepAliveTimeout;
  }

  public void setKeepAliveTimeout(final Timeout keepAliveTimeout) {
    this.keepAliveTimeout = keepAliveTimeout;
  }

  public void close() {
    getSubscriberMap().values().forEach(sub -> sub.cancel());
    if (!getKeepAliveTimeout().cancel()) {
      LOG.warn("Failed to cancel keep alive timer for channel with ID " + channelID);
    }
  }
}
