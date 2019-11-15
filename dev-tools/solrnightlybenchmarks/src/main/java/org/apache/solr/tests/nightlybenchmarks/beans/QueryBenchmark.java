package org.apache.solr.tests.nightlybenchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueryBenchmark {
  @JsonProperty("name")
  public String name;

  @JsonProperty("description")
  public String description;
  
  @JsonProperty("collection")
  public String collection;

  @JsonProperty("query-file")
  public String queryFile;

  @JsonProperty("client-type")
  public String clientType;

  @JsonProperty("min-threads")
  public int minThreads;

  @JsonProperty("max-threads")
  public int maxThreads;
}
