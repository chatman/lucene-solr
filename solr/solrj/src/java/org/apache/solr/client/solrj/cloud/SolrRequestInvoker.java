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

package org.apache.solr.client.solrj.cloud;


import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.Utils;

public interface SolrRequestInvoker {
  /**
   * Make a request to one random replica
   *
   * @param request
   * @param responseConsumer
   */
  void request(Request request, Utils.InputStreamConsumer responseConsumer) throws SolrException;

  enum Type {
    QUERY, UPDATE, ADMIN, DIRECT
  }

  interface Request {
    /**
     * Solr node name
     */
    String node();

    /**If this request is constructed based on optimistic assumptions of cached state,
     * @return null if all states are fresh
     */
    StateAssumption getStateAssumptions();


    /**
     * Full path. It could be a uri to a core or an admin
     * if the path starts with '/api/" it is a V2 API, if it starts with '/solr/' it's a v1 API
     */
    String path();

    /**
     * Request params
     */
    SolrParams params();

    /**
     * Payload if any
     */
    RequestWriter.ContentWriter payload();

    /**
     * Http Method
     */
    SolrRequest.METHOD method();

    /**
     * If request is failed due to an invalid state ERR,
     * The request object will be asked to refresh itself and fetch a new node/path
     * @param staleCollectionStates  refresh the state.json of these collections before retrying
     * @param  staleShardTerms refresh the {collection}/{shard} paths before retrying
     * @return true if a refresh is done and it makes sense to make another request
     */
    boolean refreshForRetry(Set<String> staleCollectionStates, Set<String> staleShardTerms);
  }

  /**
   * Make a request to a replica of given Collection/Shard
   */
  abstract class ReplicaReq implements Request {
  }


  /**
   * Make a request to  non-collection non core resource
   */
  abstract class NonReplicaRequest implements Request {

  }
  public class StateAssumption {
    Map<String, Integer> collectionVersions;
    Map<String, Integer> shardTermsVersions;

  }


}
