package org.apache.solr.tests.nightlybenchmarks;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.common.SolrInputDocument;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BenchmarksMain {

    final static int NODES = 5;

    public static void main(String[] args) throws Exception {

        String commitId = "e782082e711286a4c1a6ca101a9fa11bafab7b0d";

        if (args.length>0 && args[0].length()==40) {
            commitId = args[0];
        }

        SolrCloud solrCloud = new SolrCloud(3, commitId);

        try {
            solrCloud.init();

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
                            queryBench(solrCloud,collectionName,i,json);
                            long end = System.nanoTime();

                            if (i != maxThreads) {
                                solrCloud.deleteCollection(collectionName);
                            }

                            timings.put("Threads: " + i, String.valueOf((end-start)/10000000.0));
                        } else {
                            /*createCore(standalone, coreName);
 *                         index (standalone, collectionName, i, datasetFile, outputCSV);
 *                                                 if (i != maxThreads) {
 *                                                                             deleteCore(standalone, collectionName);
 *                                                                                                     }*/

                        }

                    }

                    System.out.println("Final metrics: "+timings);
                    Util.outputMetrics(outputCSV, timings);
                }

            }

        } finally {
            solrCloud.shutdown();
        }

        FileUtils.copyFile(new File("config.json"), new File("src/main/webapp/data/config.json"));
    }

    private static void queryBench(SolrCloud solrCloud, String collectionName, int i, JSONObject json) throws IOException {
        JSONArray queryBenchmarks = (JSONArray) json.get("query-benchmarks");
        Iterator it = queryBenchmarks.iterator();
        while (it.hasNext()) {
            JSONObject benchmark = (JSONObject) it.next();
            int minThreads = (int) (long) benchmark.get("min-threads");
            int maxThreads = (int) (long) benchmark.get("max-threads");
            String datasetFile = (String) benchmark.get("query-file");
            for (i = minThreads; i <= maxThreads; i++) {
                query(solrCloud, collectionName, datasetFile, i);
            }
        }
    }

    private static void query(SolrCloud solrCloud, String collectionName, String datasetFile, int i) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(i);
        File file = new File(datasetFile);
        ArrayList<String> queries = fileToList(file);
        Long st = System.currentTimeMillis();
        for (String query : queries) {
            Runnable worker = new QueryThread( query,collectionName);
            executor.execute(worker);
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
Long time=((System.currentTimeMillis() - st) / 1000);
        if(time>0) {
            System.out.println("QPS:" + new Float(queries.size() / time)+" Thread:"+i);
}    
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
    public QueryThread( String query,String collectionName) {
        this.query = query;
        this.collectionName=collectionName;
    }

    public void run() {
        SolrQuery q = new SolrQuery(query);
        try {
            CloudSolrClient cloudClient = new CloudSolrClient.Builder()
                    .withZkHost("127.0.0.1:2181").build();
            cloudClient.connect();
            cloudClient.setDefaultCollection(collectionName);
            Long count = cloudClient.query(q).getResults().getNumFound();
         //   System.out.println("Query:" + query + " count:" + count);
            cloudClient.close();
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
