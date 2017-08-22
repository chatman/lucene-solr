package org.apache.solr.tests.nightlybenchmarks;

import java.util.LinkedList;
import java.util.List;

public class Benchmarks {
	
	public static List<BenchmarkConfiguration> configurations = Util.getBenchmarkConfigurations();
	public static List<BenchmarkResponse> responses = new LinkedList<BenchmarkResponse>();
	
	public static void runBenchmarks() {
		
		try {
	
			for(BenchmarkConfiguration configuration : configurations) {

				if (configuration.benchmarkType.equals("Indexing")) {
					if (configuration.benchmarkOn.equals("SolrStandaloneMode")) {
	
						SolrNode node = new SolrNode(configuration.commitID, "", "", false);
						node.doAction(SolrNodeAction.NODE_START);
						SolrIndexingClient client = new SolrIndexingClient("localhost", node.port, configuration.commitID);

							responses.add(new BenchmarkResponse(client.indexData(configuration,
									node.getBaseUrl() + node.collectionName, null, 0, true, true, null, null)));
						
						node.doAction(SolrNodeAction.NODE_STOP);
						node.cleanup();
					
					} else if (configuration.benchmarkOn.equals("SolrCloudMode")) {
						
						SolrCloud cloud = new SolrCloud(configuration, null, "localhost", true);
						SolrIndexingClient client = new SolrIndexingClient("localhost", cloud.port, configuration.commitID);

							responses.add(new BenchmarkResponse(client.indexData(configuration,
									cloud.getuRL(),	cloud.collectionName, 0, true, true, null, null)));
						
						cloud.shutdown();
						
					}
				} else if (configuration.benchmarkType.equals("Querying")) {
					if (configuration.benchmarkOn.equals("SolrStandaloneMode")) {
						
						
						
						
						
					} else if (configuration.benchmarkOn.equals("SolrCloudMode")) {
						
						
						
						
						
					}
				}
			}
		
		} catch (Exception e) {
			
		}
	}
}