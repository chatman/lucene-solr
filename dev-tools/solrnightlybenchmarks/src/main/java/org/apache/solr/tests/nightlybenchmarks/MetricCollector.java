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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.apache.solr.tests.nightlybenchmarks.BenchmarkAppConnector.FileType;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * This class provides the implementation for metric collector.
 * @author Vivek Narang
 *
 */
public class MetricCollector extends Thread {
	
	public final static Logger logger = Logger.getLogger(BenchmarkAppConnector.class);
	public static String metricsURL;

	public String port;
	public BenchmarkConfiguration configuration;

	/**
	 * An enum defining metric types.
	 */
	public enum MetricType {
		MEM_ESTIMATION, CPU_ESTIMATION
	}

	/**
	 * Constructor.
	 * 
	 * @param commitID
	 * @param testType
	 * @param port
	 */
	public MetricCollector(BenchmarkConfiguration configuration, String port) {
		this.port = port;
		this.configuration = configuration;
	}

	/**
	 * A method invoked by the running metric estimation thread.
	 */
	public void run() {

		while (true) {
			try {

				String response = Util.getResponse(
						"http://localhost:" + this.port + "/solr/admin/metrics?wt=json&group=jvm",
						MediaType.APPLICATION_JSON);
				JSONObject jsonObject = (JSONObject) JSONValue.parse(response);

				Date dNow = new Date();
				SimpleDateFormat ft = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				BenchmarkAppConnector.writeToWebAppDataFile(
						Util.TEST_ID + "_" + MetricType.MEM_ESTIMATION + "_" + configuration.toString()
								+ ".csv",
						ft.format(dNow) + ", " + Util.TEST_ID + ", "
								+ (Double.parseDouble(
										((JSONObject) ((JSONObject) jsonObject.get("metrics")).get("solr.jvm"))
												.get("memory.heap.used").toString())
										/ (1024 * 1024)),
						false, FileType.MEMORY_HEAP_USED);
				BenchmarkAppConnector.writeToWebAppDataFile(
						Util.TEST_ID + "_" + MetricType.CPU_ESTIMATION + "_" + configuration.toString()
								+ ".csv",
						ft.format(dNow) + ", " + Util.TEST_ID + ", "
								+ (Double.parseDouble(
										((JSONObject) ((JSONObject) jsonObject.get("metrics")).get("solr.jvm"))
												.get("os.processCpuLoad").toString())
										* 100),
						false, FileType.PROCESS_CPU_LOAD);

				Thread.sleep(Integer.parseInt(Util.METRIC_ESTIMATION_PERIOD));
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
				throw new RuntimeException(e.getMessage());
			} catch (NumberFormatException e) {
				logger.error(e.getMessage());
				throw new RuntimeException(e.getMessage());
			} catch (IOException e) {
				logger.error(e.getMessage());
				throw new RuntimeException(e.getMessage());
			} catch (Exception e) {
				logger.error(e.getMessage());
				throw new RuntimeException(e.getMessage());
			}
		}

	}
}