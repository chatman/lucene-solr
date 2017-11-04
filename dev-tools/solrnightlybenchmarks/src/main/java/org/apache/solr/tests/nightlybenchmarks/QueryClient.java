/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.tests.nightlybenchmarks;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

/**
 * This class provides implementation for Query Client for Solr Standalone and
 * Solr Cloud.
 * 
 * @author Vivek Narang
 *
 */
public class QueryClient implements Runnable {

	/**
	 * A enum defining the type of client to use for querying.
	 */
	public enum QueryClientType {
		HTTP_SOLR_CLIENT, CLOUD_SOLR_CLIENT
	}

	public final static Logger logger = Logger.getLogger(QueryClient.class);
	public static ConcurrentLinkedQueue<String> queryQueue = new ConcurrentLinkedQueue<String>();
	public static boolean running;
	public static long queryCount = 0;
	public static long totalQTime = 0;
	public static long minQtime = Long.MAX_VALUE;
	public static long maxQtime = Long.MIN_VALUE;
	public static long queryFailureCount = 0;
	public static long threadReadyCount = 0;
	public static DescriptiveStatistics percentiles;
	public static boolean percentilesObjectCreated = false;
	public static long[] qTimePercentileList = new long[1000000];
	public static int qTimePercentileListPointer = 0;
	public static long queryCountLimit;
	public static long startTime = 0;
	public static long endTime = 0;
	public static boolean startTimeLock = false;
	public static long threadGroupStartTime = 0;
	public static String queryFileName;

	CountDownLatch latch;
	String urlString;
	String collectionName;
	SolrParams params;
	SolrClient solrClient;
	int threadID;
	boolean setThreadReadyFlag = false;
	long numberOfThreads = 0;
	long delayEstimationBySeconds = 0;
	Random random = new Random();

	/**
	 * Constructor.
	 * 
	 * @param urlString
	 * @param collectionName
	 * @param queryType
	 * @param numberOfThreads
	 * @param delayEstimationBySeconds
	 */
	public QueryClient(String urlString, String collectionName, long numberOfThreads, long delayEstimationBySeconds,
			QueryClientType queryClientType, String zookeeperURL, CountDownLatch latch, String queryFileName) {

		super();
		this.urlString = urlString;
		this.collectionName = collectionName;
		this.numberOfThreads = numberOfThreads;
		this.delayEstimationBySeconds = delayEstimationBySeconds;
		this.latch = latch;
		QueryClient.queryFileName = queryFileName;

		if (queryClientType == QueryClientType.HTTP_SOLR_CLIENT) {
			solrClient = new HttpSolrClient.Builder(urlString).build();
		} else if (queryClientType == QueryClientType.CLOUD_SOLR_CLIENT) {
			solrClient = new CloudSolrClient.Builder().withZkHost(zookeeperURL).build();
		}

		logger.debug(this.toString() + " QUERY CLIENT CREATED ... ");
	}

	/**
	 * A method invoked to inject the query terms in the data variables for the
	 * threads to use.
	 * 
	 * @throws Exception
	 */
	public static void prepare() throws Exception {

		logger.debug("Preparing Term Query queue ...");

		queryQueue = new ConcurrentLinkedQueue<String>();

		String line = "";
		try (BufferedReader br = new BufferedReader(new FileReader(QueryClient.queryFileName))) {

			while ((line = br.readLine()) != null) {
				queryQueue.add(line.trim());
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			throw new Exception(e.getMessage());
		}
		logger.debug("Preparing Term Query queue [COMPLETE] ...");

		logger.debug("Starting State:| " + queryQueue.size());
	}

	/**
	 * A method used by various query threads.
	 */
	public void run() {

		long elapsedTime;

		NamedList<String> requestParams = new NamedList<>();
		requestParams.add("defType", "edismax");
		requestParams.add("wt", "json");

		threadGroupStartTime = System.currentTimeMillis();
		while (true) {

			if (!setThreadReadyFlag) {
				setThreadReadyFlag = true;
				setThreadReadyCount();
			}

			if (running == true) {
				// Critical Section: When actual querying begins.
				SolrResponse response = null;
				try {

					if ((System.currentTimeMillis() - threadGroupStartTime) >= (delayEstimationBySeconds * 1000)) {

						if (!startTimeLock) {
							startTime = System.currentTimeMillis();
							startTimeLock = true;
						}

						requestParams.remove("q");
						requestParams.remove("sort");
						requestParams.remove("hl");
						requestParams.remove("hl.fl");
						requestParams.remove("facet");
						requestParams.remove("facet.field");
						requestParams.remove("facet.range");
						requestParams.remove("f.Int2_i.facet.range.start");
						requestParams.remove("f.Int2_i.facet.range.end");
						requestParams.remove("f.Int2_i.facet.range.gap");
						requestParams.remove("json.facet");

						requestParams.add("q", queryQueue.poll());

						params = SolrParams.toSolrParams(requestParams);
						response = this.fireQuery(collectionName, params);

						setQueryCounter();
						elapsedTime = response.getElapsedTime();
						setTotalQTime(elapsedTime);
						setMinMaxQTime(elapsedTime);

						if (QueryClient.queryCount >= QueryClient.queryCountLimit) {
							if (QueryClient.running != false) {

								QueryClient.running = false;
								endTime = System.currentTimeMillis();

								logger.debug("Ending State: | " + queryQueue.size());
							}
							logger.debug(this.toString() + " Getting out of critical section from check block ...");
							break;
						}
					} else {
						// This is deliberately done to warm up document cache.
						requestParams.remove("q");
						requestParams.add("q", "*:*");
						params = SolrParams.toSolrParams(requestParams);
						response = solrClient.query(collectionName, params);
					}

				} catch (SolrServerException | IOException e) {
					setQueryFailureCount();
					logger.error(e.getMessage());
					throw new RuntimeException(e.getMessage());
				}

			} else if (running == false) {
				// Break out from loop ...
				logger.debug("Ending State: | " + queryQueue.size());

				logger.debug(this.toString() + " Getting out of critical section ...");
				break;
			}
		}

		if (solrClient != null) {
			try {
				solrClient.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				throw new RuntimeException(e.getMessage());
			}
		}

		latch.countDown();
		return;
	}

	/**
	 * A method used by running threads to fire a query.
	 * 
	 * @param collectionName
	 * @param params
	 * @return
	 * @throws SolrServerException
	 * @throws IOException
	 */
	private synchronized SolrResponse fireQuery(String collectionName, SolrParams params)
			throws SolrServerException, IOException {

		return solrClient.query(collectionName, params, METHOD.POST);
	}

	/**
	 * A method to count the number of queries executed by all the threads.
	 */
	private synchronized void setQueryCounter() {

		if (running == false) {
			return;
		}
		queryCount++;
	}

	/**
	 * A method used by threads to sum up the total qtime for all the queries.
	 * 
	 * @param qTime
	 */
	private synchronized void setTotalQTime(long qTime) {

		if (running == false) {
			return;
		}
		totalQTime += qTime;
	}

	/**
	 * A method used by the running threads to count the number of queries
	 * failing.
	 */
	private synchronized void setQueryFailureCount() {
		queryFailureCount++;
	}

	/**
	 * A method used by the threads to count the number of threads up and ready
	 * for running.
	 */
	private synchronized void setThreadReadyCount() {
		threadReadyCount++;
	}

	/**
	 * A method called by the running methods to compute the minumum and maximum
	 * qtime across all the queries fired.
	 * 
	 * @param QTime
	 */
	private synchronized void setMinMaxQTime(long QTime) {

		if (running == false) {
			return;
		}

		qTimePercentileList[qTimePercentileListPointer++] = QTime;

		if (QTime < minQtime) {
			minQtime = QTime;
		}
		if (QTime > maxQtime) {
			maxQtime = QTime;
		}
	}

	/**
	 * A method used to compute the nth percentile Qtime for all the queries
	 * fired by all the threads.
	 * 
	 * @param percentile
	 * @return
	 */
	public static double getNthPercentileQTime(double percentile) {

		if (!percentilesObjectCreated) {

			double[] finalQtime = new double[qTimePercentileListPointer];
			for (int i = 0; i < (finalQtime.length); i++) {
				finalQtime[i] = qTimePercentileList[i];
			}
			Arrays.sort(finalQtime);
			percentiles = new DescriptiveStatistics(finalQtime);
			percentilesObjectCreated = true;
		}

		return percentiles.getPercentile(percentile);
	}

	/**
	 * A method used to for reseting the static data variables to get ready for
	 * the next cycle.
	 */
	public static void reset() {

		queryQueue = new ConcurrentLinkedQueue<String>();
		running = false;
		queryCount = 0;
		minQtime = Long.MAX_VALUE;
		maxQtime = Long.MIN_VALUE;
		queryFailureCount = 0;
		threadReadyCount = 0;
		percentiles = null;
		percentilesObjectCreated = false;
		qTimePercentileList = new long[1000000];
		qTimePercentileListPointer = 0;
		totalQTime = 0;
		queryCountLimit = 0;
		startTime = 0;
		endTime = 0;
		startTimeLock = false;
		threadGroupStartTime = 0;
	}
}