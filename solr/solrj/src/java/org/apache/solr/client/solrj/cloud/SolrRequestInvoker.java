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


import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.MapWriter;
import org.apache.solr.common.util.NamedList;

public interface SolrRequestInvoker {
  /**
   * Make a request to one random replica
   */
  NamedList<Object> request(Request request) throws Exception;

  enum Type {
    QUERY, UPDATE, ADMIN, DIRECT
  }

  interface Request {

    SolrRequest solrRequest();
    
    /**
     * If this request is constructed based on optimistic assumptions of cached state,
     * The header is encoded as follows SOLR-STATE : [{coll1:234,shard1:7876,shard2:876},{coll2:565,shard4:8665}]
     * The response header means the following
     * SOLR-STATE-RSP is absent, the response is all good
     * eg: SOLR-STATE-RSP : 0:coll1(234)/shard2(879) means request is not
     * processed because the state of coll1/shard2 is out of date and this node cannot serve the request, should retry
     * eg: SOLR-STATE-RSP : {coll1:234,shard2:879} means the request is processed successfully, but you may choose
     * to invalidate the state of shard terms coll1/shard2. The correct version is 879, (the cached version was '876')
     *
     * @return null if all states are fresh do not send any header
     */
    List<StateAssumption> getStateAssumptions();

    /**
     * If request is failed due to an invalid state ERR,
     * The request object will be asked to refresh itself and fetch a new node/path
     *
     * @param staleCollectionStates refresh the state.json of these collections before retrying
     * @param staleShardTerms       refresh the {collection}/{shard} paths before retrying
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

  class StateAssumption implements MapWriter {
    Entry collection;
    List<Entry> shards;

    @Override
    public void writeMap(EntryWriter ew) throws IOException {
      collection.writeMap(ew);
      if(shards == null) return;
      for (Entry shard : shards) shard.writeMap(ew);
    }

    class Entry implements MapWriter {
      String name;
      int version =-1;

      @Override
      public void writeMap(EntryWriter ew) throws IOException {
        ew.put(name, version);
      }
    }
  }


}
