package org.apache.solr.tests.nightlybenchmarks.beans;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {
  @JsonProperty("index-benchmarks")
  public List<IndexBenchmark> indexBenchmarks;

  @JsonProperty("query-benchmarks")
  public List<QueryBenchmark> queryBenchmarks;
}
