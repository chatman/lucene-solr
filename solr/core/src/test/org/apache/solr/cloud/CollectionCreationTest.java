/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.cloud;

import java.lang.invoke.MethodHandles;

import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionCreationTest extends SolrCloudTestCase {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Before
  public void setupCluster() throws Exception {

    configureCluster(5)
    .addConfig("conf1", TEST_PATH().resolve("configsets").resolve("cloud-analytics").resolve("conf"))
    .configure();
  }

  @After
  public void teardown() throws Exception {
    shutdownCluster();
  }
  
  @Test
  public void testCreateCollection() throws Exception {
    log.info("Test started...");
    CollectionAdminRequest.createCollection("abc", "conf1", 2, 2).process(cluster.getSolrClient());
    
    for (int i=0; i<10; i++) {
      cluster.getSolrClient().add("abc", sdoc("id", String.valueOf(i)));
    }
    cluster.getSolrClient().commit("abc");
    
    log.info("Output: "+cluster.getSolrClient().query("abc", params("q", "*:*")).toString());
    log.info("Test ended!!!");
  }

}
