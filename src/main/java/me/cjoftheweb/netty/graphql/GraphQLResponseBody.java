package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import java.util.List;

class GraphQLResponseBody {
  private final Object data;
  private final List<GraphQLError> errors;

  GraphQLResponseBody(final ExecutionResult executionResult) {
    this.data = executionResult.getData();
    this.errors = executionResult.getErrors();
  }

  @JsonProperty
  Object getData() {
    return data;
  }

  @JsonProperty
  List<GraphQLError> getErrors() {
    return errors;
  }
}
