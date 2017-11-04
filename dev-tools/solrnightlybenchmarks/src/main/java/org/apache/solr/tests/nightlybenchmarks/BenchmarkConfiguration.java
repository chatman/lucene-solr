package org.apache.solr.tests.nightlybenchmarks;

public class BenchmarkConfiguration {

	/* Commit Hash */
	public String commitID;
	/* Is it indexing or querying */
	public String benchmarkType;
	/* If for example querying, what kind of query */
	public String benchmarkSubType;
	/* Is it on standalone mode or cloud mode */
	public String benchmarkOn;
	/* What client to use for this benchmark */
	public String benchmarkClient;
	/* If on cloud mode how many nodes */
	public int nodes;
	/* If on cloud mode how many shards */
	public int shards;
	/* If on cloud mode how many replicas */
	public int replicas;
	/* What is the thread count */
	public int threadCount;
	/* What is the input/query count */
	public int inputCount;

	@Override
	public String toString() {

		String returnString = "{" + commitID + "_" + benchmarkType + "_" + benchmarkSubType + "_" + benchmarkOn + "_"
				+ benchmarkClient + "_" + nodes + "_" + shards + "_" + replicas + "_" + threadCount + "_" + inputCount
				+ "}";

		return returnString;
	}
}