package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class GraphQLWebSocketFrame {
  private final Object payload;
  private final String id;
  private final String type;

  @JsonCreator
  GraphQLWebSocketFrame(
      @JsonProperty("payload") final Object payload,
      @JsonProperty("id") final String id,
      @JsonProperty(value = "type", required = true) final String type) {
    this.payload = payload;
    this.id = id;
    this.type = type;
  }

  GraphQLWebSocketFrame(final String type) {
    this.payload = null;
    this.id = null;
    this.type = type;
  }

  @JsonProperty("payload")
  Object getPayload() {
    return this.payload;
  }

  @JsonProperty("id")
  String getId() {
    return this.id;
  }

  @JsonProperty(value = "type", required = true)
  String getType() {
    return this.type;
  }
}
