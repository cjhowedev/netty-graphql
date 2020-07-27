package me.cjoftheweb.netty.graphql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.ExecutionInput;
import java.util.Map;

class GraphQLRequestBody {
  private String query;
  private final String operationName;
  private final Map<String, Object> variables;

  @JsonCreator
  GraphQLRequestBody(
      @JsonProperty(value = "query", required = true) String query,
      @JsonProperty("operationName") String operationName,
      @JsonProperty("variables") Map<String, Object> variables) {
    this.query = query;
    this.operationName = operationName;
    this.variables = variables;
  }

  String getQuery() {
    return this.query;
  }

  void setQuery(final String query) {
    this.query = query;
  }

  String getOperationName() {
    return this.operationName;
  }

  Map<String, Object> getVariables() {
    return this.variables;
  }

  ExecutionInput convertToExecutionInput() {
    ExecutionInput.Builder executionInputBuilder = ExecutionInput.newExecutionInput();

    if (getQuery() != null) {
      executionInputBuilder.query(getQuery());
    }

    if (getOperationName() != null) {
      executionInputBuilder.operationName(getOperationName());
    }

    if (getVariables() != null) {
      executionInputBuilder.variables(getVariables());
    }

    return executionInputBuilder.build();
  }
}
