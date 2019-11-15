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
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class provides a blueprint for SolrCloud
 * @author Vivek Narang
 *
 */
public class SolrCloud {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	final int numNodes;
	Zookeeper zookeeper;
	final String commitId;
	List<SolrNode> nodes = new ArrayList<>();

	public SolrCloud(int numNodes, String commitId) throws Exception {
		this.numNodes = numNodes;
		this.commitId = commitId;
	}

	/**
	 * A method used for getting ready to set up the Solr Cloud.
	 * @throws Exception 
	 */
	public void init() throws Exception {
		zookeeper = new Zookeeper();
		int initValue = zookeeper.doAction(ZookeeperAction.ZOOKEEPER_START);
		if (initValue != 0) {
			log.error("Failed to start Zookeeper!");
			throw new RuntimeException("Failed to start Zookeeper!");
		}

		for (int i = 1; i <= numNodes; i++) {

			SolrNode node = new SolrNode(commitId, zookeeper, true);
			node.doAction(SolrNodeAction.NODE_START);
			nodes.add(node);
		}
	}

	/**
	 * A method used for creating a collection.
	 * 
	 * @param collectionName
	 * @param configName
	 * @param shards
	 * @param replicas
	 * @throws Exception 
	 */
	public void createCollection(String collectionName, String configName, int shards, int replicas) throws Exception {
		try {
			nodes.get(0).createCollection(collectionName, configName, shards, replicas);
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage());
			throw new Exception(e.getMessage());
		}
	}

	/**
	 * A method for deleting a collection.
	 * 
	 * @param collectionName
	 * @throws Exception 
	 */
	public void deleteCollection(String collectionName) throws Exception {
		try {
			nodes.get(0).deleteCollection(collectionName);
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage());
			throw new Exception(e.getMessage());
		}
	}

	/**
	 * A method used to get the zookeeper url for communication with the solr
	 * cloud.
	 * 
	 * @return String
	 */
	public String getZookeeperUrl() {
		return zookeeper.getZookeeperIp() + ":" + zookeeper.getZookeeperPort();
	}

	/**
	 * A method used for shutting down the solr cloud.
	 * @throws Exception 
	 */
	public void shutdown() throws Exception {
		for (SolrNode node : nodes) {
			node.doAction(SolrNodeAction.NODE_STOP);
			node.cleanup();
		}
		zookeeper.doAction(ZookeeperAction.ZOOKEEPER_STOP);
		zookeeper.doAction(ZookeeperAction.ZOOKEEPER_CLEAN);
	}

	public static void main(String[] args) throws Exception {
		SolrCloud cloud = new SolrCloud(3, "e782082e711286a4c1a6ca101a9fa11bafab7b0d");
		cloud.init();
		cloud.createCollection("mycollection", null, 2, 2);
		cloud.shutdown();
	}
}