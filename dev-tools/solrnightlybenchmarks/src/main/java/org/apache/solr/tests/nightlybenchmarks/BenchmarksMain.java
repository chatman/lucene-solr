package org.apache.solr.tests.nightlybenchmarks;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BenchmarksMain {

    final static int NODES = 5;

    public static void main(String[] args) throws Exception {

        //String commitId = "e782082e711286a4c1a6ca101a9fa11bafab7b0d";
    	String commitId = "a70b257c805b047588e133c5280ecfae418668b3";

        if (args.length>0 && args[0].length()==40) {
            commitId = args[0];
        }

        SolrCloud solrCloud = new SolrCloud(3, commitId);

        try {
            solrCloud.init();
            System.out.println("SolrCloud inited...");

            String commitDate = Util.getDateString(solrCloud.nodes.get(0).commitTime);

            String jsonConfig = FileUtils.readFileToString(new File("config.json"),"UTF-8");
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonConfig);


            JSONArray indexBenchmarks = (JSONArray)json.get("index-benchmarks");
            Iterator it = indexBenchmarks.iterator();
            while (it.hasNext()) {
                JSONObject benchmark = (JSONObject)it.next();

                String name = (String)benchmark.get("name");
                String description = (String)benchmark.get("description");
                String datasetFile = (String)benchmark.get("dataset-file");
                String replicationType = (String)benchmark.get("replication-type");
                JSONArray setups = (JSONArray) benchmark.get("setups");
                Iterator setupsIterator = setups.iterator();
                while (setupsIterator.hasNext()) {
                    JSONObject setup = (JSONObject) setupsIterator.next();
                    String collectionName = (String) setup.get("collection");
                    String setupName = (String) setup.get("setup-name");
                    int replicationFactor = (int)(long) setup.get("replicationFactor");
                    int shards = (int)(long) setup.get("shards");
                    int minThreads = (int)(long) setup.get("min-threads");
                    int maxThreads = (int)(long) setup.get("max-threads");

                    String outputCSV = "./src/main/webapp/data/" + setupName+".csv";

                    Map<String, String> timings = new LinkedHashMap<>();
                    timings.put("Date",  commitDate);
                    timings.put("Test_ID", commitId);
                    timings.put("CommitID",  commitId);

                    for (int i=minThreads; i<=maxThreads; i++) {
                        if ("cloud".equals(replicationType)) {
                            solrCloud.createCollection(collectionName, null, shards, replicationFactor);
                            long start = System.nanoTime();
                            index (solrCloud, collectionName, i, datasetFile);
                            long end = System.nanoTime();

                            if (i != maxThreads) {
                                solrCloud.deleteCollection(collectionName);
                            }

                            timings.put("Threads: " + i, String.valueOf((end-start)/10000000.0));
                        } // non-cloud not supported as of now
                    }

                    System.out.println("Final metrics: "+timings);
                    Util.outputMetrics(outputCSV, timings);
                }
            }
            
            System.out.println("Querying benchmarks...\n***********************\n****************");

            // Query benchmarks
            JSONArray queryBenchmarks = (JSONArray)json.get("query-benchmarks");
            it = queryBenchmarks.iterator();
            while (it.hasNext()) {
                JSONObject benchmark = (JSONObject)it.next();

                String name = (String)benchmark.get("name");
                String description = (String)benchmark.get("description");
                String queryFile = (String)benchmark.get("query-file");
                String replicationType = (String)benchmark.get("replication-type");
                String collection = (String)benchmark.get("collection");
                int minThreads = (int)(long) benchmark.get("min-threads");
                int maxThreads = (int)(long) benchmark.get("max-threads");

                String outputCSV = "./src/main/webapp/data/" + name +".csv";

                Map<String, String> timings = new LinkedHashMap<>();
                timings.put("Date",  commitDate);
                timings.put("Test_ID", commitId);
                timings.put("CommitID",  commitId);

                for (int threads = minThreads; threads <= maxThreads; threads++) {
                	ExecutorService executor = Executors.newFixedThreadPool(threads);
                	List<String> queries = FileUtils.readLines(new File(queryFile), "UTF-8");
                	
                	String solr1 = solrCloud.nodes.get(0).getBaseUrl()+collection;
                	HttpSolrClient client = new HttpSolrClient(solr1);

                	long start = System.currentTimeMillis();
                	for (String query : queries) {
                		Runnable worker = new QueryThread(client, query, collection);
                		executor.execute(worker);
                	}
                	executor.shutdown();
                	executor.awaitTermination(10, TimeUnit.SECONDS);
                	client.close();
                	long time = System.currentTimeMillis() - start;
                	System.out.println("Took time: " + time);
                	if (time > 0) {
                		double qps = queries.size() / (time / 1000.0);
                		System.out.println("QPS: " + qps+", Thread: "+threads);
                        timings.put("Threads: " + threads, String.valueOf(qps));
                	}
                }
                Util.outputMetrics(outputCSV, timings);
            }
            
        } finally {
            solrCloud.shutdown();
        }

        FileUtils.copyFile(new File("config.json"), new File("src/main/webapp/data/config.json"));
    }

    private static ArrayList<String> fileToList(File file) throws IOException {
        ArrayList<String> queries = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            queries.add(line);
        }
        br.close();
        return queries;
    }

    static void index(SolrCloud solrCloud, String collectionName, int threads, String datasetFile) throws IOException, SolrServerException {

        ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient(
                solrCloud.nodes.get(0).getBaseUrl()+collectionName, 1000, threads);
        if (datasetFile.contains("wiki")) {
            File file = new File(datasetFile);
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                String fields[] = line.split(",");
                String id = fields[0];
                String title = fields[1];
                String text = fields[2];

                SolrInputDocument doc = new SolrInputDocument("id", id, "title", title, "text", text);
                client.add(doc);
            }

            br.close();
        }
        client.commit();
        client.close();
    }

    /*static void index(StandaloneSolr standalone, String collectionName, int threads, String datasetFile, File outputCSV) {
 *         HttpSolrClient hsc = new HttpSolrClient(standalone.getUrl());
 *
 *             }*/

}

class QueryThread implements Runnable {
    private String query;
    private String collectionName;
    private SolrClient client;
    
    public QueryThread( SolrClient client, String query,String collectionName) {
        this.query = query;
        this.collectionName=collectionName;
        this.client = client;
    }

    public void run() {
        SolrQuery q = new SolrQuery(query);
        try {
        	long count = client.query(q).getResults().getNumFound();
        	System.out.println("Query:" + query + " count:" + count);
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
