package org.apache.solr.tests.nightlybenchmarks.beans;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IndexBenchmark {
  @JsonProperty("name")
  public String name;

  @JsonProperty("description")
  public String description;
  
  @JsonProperty("replication-type")
  public String replicationType;
  
  @JsonProperty("dataset-file")
  public String datasetFile;
  
  @JsonProperty("setups")
  public List<Setup> setups;

  static public class Setup {
    @JsonProperty("setup-name")
    public String name;

    @JsonProperty("collection")
    public String collection;

    @JsonProperty("replication-factor")
    public int replicationFactor;

    @JsonProperty("shards")
    public int shards;

    @JsonProperty("min-threads")
    public int minThreads;

    @JsonProperty("max-threads")
    public int maxThreads;
  }
}

