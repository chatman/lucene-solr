package org.apache.solr.tests.nightlybenchmarks;

import java.util.List;

public class BenchmarkNConfiguration {

    public List<IndexBenchmark> indexBenchmarks;
    public List<QueryBenchmark> queryBenchmarks;

}

class IndexBenchmark {
	
	public String name;
	public String description;
	public String replicationType;
	public String dataSetFile;
	public List<Setup> setups;
	
}

class Setup {
	
	public String collection;
	public int replicationFactor;
	public int shards;
	public int minThreads;
	public int maxThreads;
	
}

class QueryBenchmark {
	
	public String name;
	public String description;
	public String replicationType;
	public String collectionCore;
	public String queryFile;
	public String clientType;
	public int minThreads;
	public int maxThreads;
	
}