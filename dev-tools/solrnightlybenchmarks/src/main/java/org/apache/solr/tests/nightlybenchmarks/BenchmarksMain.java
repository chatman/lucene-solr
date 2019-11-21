package org.apache.solr.tests.nightlybenchmarks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.tests.nightlybenchmarks.beans.Configuration;
import org.apache.solr.tests.nightlybenchmarks.beans.IndexBenchmark;
import org.apache.solr.tests.nightlybenchmarks.beans.QueryBenchmark;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BenchmarksMain {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  final static int NODES = 5;

  public static void main(String[] args) throws Exception {

    Configurator.setRootLevel(Level.INFO);

    //String commitId = "e782082e711286a4c1a6ca101a9fa11bafab7b0d";
    String commitId = "400514026e71de6c39f0eabb8663f7ef3e774ceb";

    if (args.length>0 && args[0].length()==40) {
      commitId = args[0];
    }

    SolrCloud solrCloud = new SolrCloud(3, commitId);

    try {
      solrCloud.init();
      log.info("SolrCloud inited...");

      String commitDate = Util.getDateString(solrCloud.nodes.get(0).commitTime);

      String jsonConfig = FileUtils.readFileToString(new File("config.json"),"UTF-8");
      JSONParser parser = new JSONParser();
      JSONObject json = (JSONObject) parser.parse(jsonConfig);

      Configuration config = new ObjectMapper().readValue(FileUtils.readFileToString(new File("config.json"),"UTF-8"), Configuration.class);

      // Indexing benchmarks
      log.info("Starting indexing benchmarks...");
      for (IndexBenchmark benchmark: config.indexBenchmarks) {
        for (IndexBenchmark.Setup setup: benchmark.setups) {
          String outputCSV = "./src/main/webapp/data/" + setup.name + ".csv";

          Map<String, String> timings = Util.map("Date", commitDate, "Test_ID", commitId, "CommitID",  commitId);

          for (int i=setup.minThreads; i<=setup.maxThreads; i+=setup.threadStep) {
            solrCloud.createCollection(setup.collection, null, setup.shards, setup.replicationFactor);
            long start = System.nanoTime();
            index (solrCloud, setup.collection, i, benchmark.datasetFile);
            long end = System.nanoTime();

            if (i != setup.maxThreads || config.queryBenchmarks.isEmpty()) {
              solrCloud.deleteCollection(setup.collection);
            }

            timings.put("Threads: " + i, String.valueOf((end-start)/10000000.0));
          }

          System.out.println("Final metrics: "+timings);
          Util.outputMetrics(outputCSV, timings);
        }
      }

      // Query benchmarks
      log.info("Starting querying benchmarks...");
      for (QueryBenchmark benchmark: config.queryBenchmarks) {
        String outputCSV = "./src/main/webapp/data/" + benchmark.name + ".csv";

        Map<String, String> timings = Util.map("Date",  commitDate, "Test_ID", commitId, "CommitID",  commitId);

        for (int threads = benchmark.minThreads; threads <= benchmark.maxThreads; threads++) {
          ExecutorService executor = Executors.newFixedThreadPool(threads);
          HttpSolrClient client = new HttpSolrClient(solrCloud.nodes.get(0).getBaseUrl() + benchmark.collection);
          SynchronizedDescriptiveStatistics stats = new SynchronizedDescriptiveStatistics();

          List<String> queries = FileUtils.readLines(new File(benchmark.queryFile), "UTF-8");
          long start = System.currentTimeMillis();
          try {
            for (String query : queries) {
              Runnable worker = new QueryThread(client, query, stats);
              executor.submit(worker);
            }
          } finally {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            client.close();
          }
          long time = System.currentTimeMillis() - start;
          System.out.println("Took time: " + time);
          if (time > 0) {
            double qps = queries.size() / (time / 1000.0);
            System.out.println("QPS: " + qps+", Thread: "+threads+", Median latency: "+stats.getPercentile(50) + 
                ", 95th latency: "+stats.getPercentile(95));
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

  static void index(SolrCloud solrCloud, String collectionName, int threads, String datasetFile) throws IOException, SolrServerException {

    ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient(
        solrCloud.nodes.get(0).getBaseUrl()+collectionName, 10000, threads);
    if (datasetFile.endsWith(".tsv")) {
      File file = new File(datasetFile);

      BufferedReader br;

      if(datasetFile.endsWith(".lzma") || datasetFile.endsWith(".xz")) {
        LZMACompressorInputStream lzma = new LZMACompressorInputStream(new FileInputStream(file));
        br = new BufferedReader(new InputStreamReader(lzma));
      } else {
        br = new BufferedReader(new FileReader(file));
      }
      String line;
      int counter = 0;
      while ((line = br.readLine()) != null) {
        counter++;

        String fields[] = line.split("\t");
        int id = counter;
        String title = fields[0];
        String date = fields[1];
        String text = fields[2];

        SolrInputDocument doc = new SolrInputDocument("id", String.valueOf(id),
            "title", title, "timestamp_s", date, "text_t", text);
        client.add(doc);
      }

      br.close();
    }
    client.commit();
    client.close();
  }

}

class QueryThread implements Runnable {
  private String query;
  private SolrClient client;
  private SynchronizedDescriptiveStatistics stats;

  public QueryThread(SolrClient client, String query, SynchronizedDescriptiveStatistics stats) {
    this.query = query;
    this.client = client;
    this.stats = stats;
  }

  public void run() {
    SolrQuery q = new SolrQuery(query);
    try {
      long start = System.nanoTime();
      long count = client.query(q).getResults().getNumFound();
      stats.addValue((System.nanoTime() - start) / 1000_000.0);
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
