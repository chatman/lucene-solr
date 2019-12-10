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
package org.apache.solr.handler.component;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.cloud.SolrRequestInvoker;
import org.apache.solr.client.solrj.cloud.SolrRequestInvoker.Request;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.SimpleSolrResponse;
import org.apache.solr.client.solrj.routing.ReplicaListTransformer;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.cloud.CloudDescriptor;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.util.tracing.GlobalTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.opentracing.Span;
import io.opentracing.Tracer;

public class HttpShardHandler extends ShardHandler {
  
  /**
   * If the request context map has an entry with this key and Boolean.TRUE as value,
   * {@link #prepDistributed(ResponseBuilder)} will only include {@link org.apache.solr.common.cloud.Replica.Type#NRT} replicas as possible
   * destination of the distributed request (or a leader replica of type {@link org.apache.solr.common.cloud.Replica.Type#TLOG}). This is used 
   * by the RealtimeGet handler, since other types of replicas shouldn't respond to RTG requests
   */
  public static String ONLY_NRT_REPLICAS = "distribOnlyRealtime";

  private HttpShardHandlerFactory httpShardHandlerFactory;
  private CompletionService<ShardResponse> completionService;
  private Set<Future<ShardResponse>> pending;
  private Map<String,List<String>> shardToURLs;
  private Http2SolrClient http2Client;
  private HttpClient legacyClient;

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public HttpShardHandler(HttpShardHandlerFactory httpShardHandlerFactory, Http2SolrClient http2Client) {
    this(httpShardHandlerFactory);
    this.http2Client = http2Client;
  }

  @Deprecated // for backward-compatibility when we are moving from apache http client to jetty client
  public HttpShardHandler(HttpShardHandlerFactory httpShardHandlerFactory, HttpClient legacyClient) {
    this(httpShardHandlerFactory);
    this.legacyClient = legacyClient;
  }

  private HttpShardHandler(HttpShardHandlerFactory httpShardHandlerFactory) {
    this.httpShardHandlerFactory = httpShardHandlerFactory;
    completionService = httpShardHandlerFactory.newCompletionService();
    pending = new HashSet<>();

    // maps "localhost:8983|localhost:7574" to a shuffled List("http://localhost:8983","http://localhost:7574")
    // This is primarily to keep track of what order we should use to query the replicas of a shard
    // so that we use the same replica for all phases of a distributed request.
    shardToURLs = new HashMap<>();    
  }

  // Not thread safe... don't use in Callable.
  // Don't modify the returned URL list.
  private List<String> getURLs(String shard) {
    List<String> urls = shardToURLs.get(shard);
    if (urls == null) {
      urls = httpShardHandlerFactory.buildURLList(shard);
      shardToURLs.put(shard, urls);
    }
    return urls;
  }


  @Override
  public void submit(final ShardRequest sreq, final String shard, final ModifiableSolrParams params) {
    // do this outside of the callable for thread safety reasons
    final List<String> urls = getURLs(shard);
    final Tracer tracer = GlobalTracer.getTracer();
    final Span span = tracer != null? tracer.activeSpan() : null;

    SolrRequestInvoker requestInvoker = new LegacyRequestInvoker(http2Client, legacyClient, urls, httpShardHandlerFactory.getLoadBalancer(),
        httpShardHandlerFactory.permittedLoadBalancerRequestsMinimumAbsolute, httpShardHandlerFactory.permittedLoadBalancerRequestsMaximumFraction);

    Callable<ShardResponse> task = () -> {
      SimpleSolrResponse ssr = new SimpleSolrResponse();
      ShardResponse srsp = ((LegacyRequestInvoker)requestInvoker).wrapSimpleResponseToShardResponse(sreq, shard, ssr);

      long startTime = System.nanoTime();
      try {
        Request invocationRequest = ((LegacyRequestInvoker)requestInvoker).getInvocationRequest(sreq, shard, params, urls, tracer, span);
        ssr.nl = requestInvoker.request(invocationRequest);
        srsp.setShardAddress(invocationRequest.solrRequest().getBasePath());
      } catch (SolrException th) {
        Throwable ex = th.getCause();
        srsp.setException(ex);
        if (ex instanceof SolrException) {
          srsp.setResponseCode(((SolrException)ex).code());
        } else {
          srsp.setResponseCode(-1);
        }
      }

      ssr.elapsedTime = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

      return srsp;
    };

    try {
      if (shard != null)  {
        MDC.put("ShardRequest.shards", shard);
      }
      if (urls != null && !urls.isEmpty())  {
        MDC.put("ShardRequest.urlList", urls.toString());
      }
      pending.add( completionService.submit(task) );
    } finally {
      MDC.remove("ShardRequest.shards");
      MDC.remove("ShardRequest.urlList");
    }
  }

  /** returns a ShardResponse of the last response correlated with a ShardRequest.  This won't 
   * return early if it runs into an error.  
   **/
  @Override
  public ShardResponse takeCompletedIncludingErrors() {
    return take(false);
  }


  /** returns a ShardResponse of the last response correlated with a ShardRequest,
   * or immediately returns a ShardResponse if there was an error detected
   */
  @Override
  public ShardResponse takeCompletedOrError() {
    return take(true);
  }
  
  private ShardResponse take(boolean bailOnError) {
    
    while (pending.size() > 0) {
      try {
        Future<ShardResponse> future = completionService.take();
        pending.remove(future);
        ShardResponse rsp = future.get();
        if (bailOnError && rsp.getException() != null) return rsp; // if exception, return immediately
        // add response to the response list... we do this after the take() and
        // not after the completion of "call" so we know when the last response
        // for a request was received.  Otherwise we might return the same
        // request more than once.
        rsp.getShardRequest().responses.add(rsp);
        if (rsp.getShardRequest().responses.size() == rsp.getShardRequest().actualShards.length) {
          return rsp;
        }
      } catch (InterruptedException e) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
      } catch (ExecutionException e) {
        // should be impossible... the problem with catching the exception
        // at this level is we don't know what ShardRequest it applied to
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Impossible Exception",e);
      }
    }
    return null;
  }


  @Override
  public void cancelAll() {
    for (Future<ShardResponse> future : pending) {
      future.cancel(false);
    }
  }

  @Override
  public void prepDistributed(ResponseBuilder rb) {
    final SolrQueryRequest req = rb.req;
    final SolrParams params = req.getParams();
    final String shards = params.get(ShardParams.SHARDS);
    
    // since the cost of grabbing cloud state is still up in the air, we grab it only
    // if we need it.
    ClusterState clusterState = null;
    Map<String,Slice> slices = null;
    CoreDescriptor coreDescriptor = req.getCore().getCoreDescriptor();
    CloudDescriptor cloudDescriptor = coreDescriptor.getCloudDescriptor();
    ZkController zkController = req.getCore().getCoreContainer().getZkController();

    // Populate the slices
    if (zkController != null) {
      // SolrCloud mode
      if (shards != null) {
        computeSlicesFromShardsParameter(rb, shards);
      } else {
        // we weren't provided with an explicit list of slices to query via "shards", so use the cluster state
        clusterState =  zkController.getClusterState();
        slices = computeSlicesFromClusterState(rb, params, clusterState, cloudDescriptor);
      }
      // Map slices to shards
      // nocommit if slices is null, then this method slices doesn't reset this into an object
      boolean shortCircuit = mapSlicesToShards(rb, req, params, shards, clusterState, slices, coreDescriptor, cloudDescriptor, zkController);
      if (shortCircuit) {
        return;
      }
      System.out.println("Prep Slices: "+slices);
      System.out.println("Prep Shards: "+shards);
    } else {
      // Standalone mode
      // In standalone mode, we need host whitelist checking
      HttpShardHandlerFactory.WhitelistHostChecker hostChecker = httpShardHandlerFactory.getWhitelistHostChecker();
      if (hostChecker.isWhitelistHostCheckingEnabled() && !hostChecker.hasExplicitWhitelist()) {
        throw new SolrException(ErrorCode.FORBIDDEN, "HttpShardHandlerFactory "+HttpShardHandlerFactory.INIT_SHARDS_WHITELIST
            +" not configured but required (in lieu of ZkController and ClusterState) when using the '"+ShardParams.SHARDS+"' parameter."
            +HttpShardHandlerFactory.SET_SOLR_DISABLE_SHARDS_WHITELIST_CLUE);
      }
      List<String> lst = StrUtils.splitSmart(shards, ",", true);
      rb.shards = lst.toArray(new String[lst.size()]);
      rb.slices = new String[rb.shards.length];
      
      if (shards != null) {
        // No cloud, verbatim check of shards
        hostChecker = httpShardHandlerFactory.getWhitelistHostChecker();
        hostChecker.checkWhitelist(shards, new ArrayList<>(Arrays.asList(shards.split("[,|]"))));
      }
      
      System.out.println("Prep shards param: "+shards);
      System.out.println("Prep standalone Slices: "+Arrays.toString(rb.slices));
      System.out.println("Prep standalone Shards: "+Arrays.toString(rb.shards));

    }

    String shards_rows = params.get(ShardParams.SHARDS_ROWS);
    if(shards_rows != null) {
      rb.shards_rows = Integer.parseInt(shards_rows);
    }
    String shards_start = params.get(ShardParams.SHARDS_START);
    if(shards_start != null) {
      rb.shards_start = Integer.parseInt(shards_start);
    }
  }

  /*
   * @return true if shortcircuited, false if completed properly.
   */
  private boolean mapSlicesToShards(ResponseBuilder rb, final SolrQueryRequest req, final SolrParams params,
      final String shards, ClusterState clusterState, Map<String,Slice> slices, CoreDescriptor coreDescriptor,
      CloudDescriptor cloudDescriptor, ZkController zkController) {
    assert zkController != null: "We should be in cloud mode if we reach this point";
    assert rb.slices != null: "We shouldn't be here if slices are not populated till now";

    HttpShardHandlerFactory.WhitelistHostChecker hostChecker = httpShardHandlerFactory.getWhitelistHostChecker();
    final ReplicaListTransformer replicaListTransformer = httpShardHandlerFactory.getReplicaListTransformer(req);

    // Are we hosting the shard that this request is for, and are we active? If so, then handle it ourselves
    // and make it a non-distributed request.
    String ourSlice = cloudDescriptor.getShardId();
    String ourCollection = cloudDescriptor.getCollectionName();
    // Some requests may only be fulfilled by replicas of type Replica.Type.NRT
    boolean onlyNrtReplicas = Boolean.TRUE == req.getContext().get(ONLY_NRT_REPLICAS);
    if (rb.slices.length == 1 && rb.slices[0] != null
        && ( rb.slices[0].equals(ourSlice) || rb.slices[0].equals(ourCollection + "_" + ourSlice) )  // handle the <collection>_<slice> format
        && cloudDescriptor.getLastPublished() == Replica.State.ACTIVE
        && (!onlyNrtReplicas || cloudDescriptor.getReplicaType() == Replica.Type.NRT)) {
      boolean shortCircuit = params.getBool("shortCircuit", true);       // currently just a debugging parameter to check distrib search on a single node

      String targetHandler = params.get(ShardParams.SHARDS_QT);
      shortCircuit = shortCircuit && targetHandler == null;             // if a different handler is specified, don't short-circuit

      if (shortCircuit) {
        rb.isDistrib = false;
        rb.shortCircuitedURL = ZkCoreNodeProps.getCoreUrl(zkController.getBaseUrl(), coreDescriptor.getName());
        if (hostChecker.isWhitelistHostCheckingEnabled() && hostChecker.hasExplicitWhitelist()) {
          // We only need to check the host whitelist if there is an explicit whitelist (other than all the live nodes)
          // when the "shards" indicate cluster state elements only
          hostChecker.checkWhitelist(clusterState, shards, Arrays.asList(rb.shortCircuitedURL));
        }
        return true;
      }
      // We shouldn't need to do anything to handle "shard.rows" since it was previously meant to be an optimization?
    }
    
    if (clusterState == null && zkController != null) {
      clusterState =  zkController.getClusterState();
    }


    for (int i=0; i<rb.shards.length; i++) {
      if (rb.shards[i] != null) {
        final List<String> shardUrls = StrUtils.splitSmart(rb.shards[i], "|", true);
        replicaListTransformer.transform(shardUrls);
        hostChecker.checkWhitelist(clusterState, shards, shardUrls);
        // And now recreate the | delimited list of equivalent servers
        rb.shards[i] = createSliceShardsStr(shardUrls);
      } else {
        if (slices == null) {
          slices = clusterState.getCollection(cloudDescriptor.getCollectionName()).getSlicesMap();
        }
        String sliceName = rb.slices[i];

        Slice slice = slices.get(sliceName);

        if (slice==null) {
          // Treat this the same as "all servers down" for a slice, and let things continue
          // if partial results are acceptable
          rb.shards[i] = "";
          continue;
          // throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "no such shard: " + sliceName);
        }
        final Predicate<Replica> isShardLeader = new Predicate<Replica>() {
          private Replica shardLeader = null;

          @Override
          public boolean test(Replica replica) {
            if (shardLeader == null) {
              try {
                shardLeader = zkController.getZkStateReader().getLeaderRetry(cloudDescriptor.getCollectionName(), slice.getName());
              } catch (InterruptedException e) {
                throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE, "Exception finding leader for shard " + slice.getName() + " in collection " 
                    + cloudDescriptor.getCollectionName(), e);
              } catch (SolrException e) {
                if (log.isDebugEnabled()) {
                  log.debug("Exception finding leader for shard {} in collection {}. Collection State: {}", 
                      slice.getName(), cloudDescriptor.getCollectionName(), zkController.getZkStateReader().getClusterState().getCollectionOrNull(cloudDescriptor.getCollectionName()));
                }
                throw e;
              }
            }
            return replica.getName().equals(shardLeader.getName());
          }
        };

        final List<Replica> eligibleSliceReplicas = collectEligibleReplicas(slice, clusterState, onlyNrtReplicas, isShardLeader);

        final List<String> shardUrls = transformReplicasToShardUrls(replicaListTransformer, eligibleSliceReplicas);

        if (hostChecker.isWhitelistHostCheckingEnabled() && hostChecker.hasExplicitWhitelist()) {
          /*
           * We only need to check the host whitelist if there is an explicit whitelist (other than all the live nodes)
           * when the "shards" indicate cluster state elements only
           */
          hostChecker.checkWhitelist(clusterState, shards, shardUrls);
        }

        // And now recreate the | delimited list of equivalent servers
        final String sliceShardsStr = createSliceShardsStr(shardUrls);
        if (sliceShardsStr.isEmpty()) {
          boolean tolerant = ShardParams.getShardsTolerantAsBool(rb.req.getParams());
          if (!tolerant) {
            // stop the check when there are no replicas available for a shard
            throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE,
                "no servers hosting shard: " + rb.slices[i]);
          }
        }
        rb.shards[i] = sliceShardsStr;
      }
    }
    return false;
  }

  private void computeSlicesFromShardsParameter(ResponseBuilder rb, final String shards) {
    List<String> lst = StrUtils.splitSmart(shards, ",", true);
    rb.shards = lst.toArray(new String[lst.size()]);
    rb.slices = new String[rb.shards.length];
    // figure out which shards are slices
    for (int i=0; i<rb.shards.length; i++) {
      if (rb.shards[i].indexOf('/') < 0) {
        // this is a logical shard
        rb.slices[i] = rb.shards[i];
        rb.shards[i] = null;
      }
    }
  }

  /*
   * Use the clusterstate and compute the slices to query for this distributed request.
   * Populates these slices to the response builder (rb.slices).
   * 
   * @return Map of key as slice name and value as Slice instance
   */
  private Map<String,Slice> computeSlicesFromClusterState(ResponseBuilder rb, final SolrParams params, ClusterState clusterState,
      CloudDescriptor cloudDescriptor) {
    Map<String,Slice> slices;
    String shardKeys =  params.get(ShardParams._ROUTE_);

    // This will be the complete list of slices we need to query for this request.
    slices = new HashMap<>();

    // we need to find out what collections this request is for.

    // A comma-separated list of specified collections.
    // Eg: "collection1,collection2,collection3"
    String collections = params.get("collection");
    if (collections != null) {
      // If there were one or more collections specified in the query, split
      // each parameter and store as a separate member of a List.
      List<String> collectionList = StrUtils.splitSmart(collections, ",",
          true);
      // In turn, retrieve the slices that cover each collection from the
      // cloud state and add them to the Map 'slices'.
      for (String collectionName : collectionList) {
        // The original code produced <collection-name>_<shard-name> when the collections
        // parameter was specified (see ClientUtils.appendMap)
        // Is this necessary if ony one collection is specified?
        // i.e. should we change multiCollection to collectionList.size() > 1?
        addSlices(slices, clusterState, params, collectionName,  shardKeys, true);
      }
    } else {
      // just this collection
      String collectionName = cloudDescriptor.getCollectionName();
      addSlices(slices, clusterState, params, collectionName,  shardKeys, false);
    }


    // Store the logical slices in the ResponseBuilder and create a new
    // String array to hold the physical shards (which will be mapped
    // later).
    rb.slices = slices.keySet().toArray(new String[slices.size()]);
    rb.shards = new String[rb.slices.length];
    return slices;
  }

  private static List<Replica> collectEligibleReplicas(Slice slice, ClusterState clusterState, boolean onlyNrtReplicas, Predicate<Replica> isShardLeader) {
    final Collection<Replica> allSliceReplicas = slice.getReplicasMap().values();
    final List<Replica> eligibleSliceReplicas = new ArrayList<>(allSliceReplicas.size());
    for (Replica replica : allSliceReplicas) {
      if (!clusterState.liveNodesContain(replica.getNodeName())
          || replica.getState() != Replica.State.ACTIVE
          || (onlyNrtReplicas && replica.getType() == Replica.Type.PULL)) {
        continue;
      }

      if (onlyNrtReplicas && replica.getType() == Replica.Type.TLOG) {
        if (!isShardLeader.test(replica)) {
          continue;
        }
      }
      eligibleSliceReplicas.add(replica);
    }
    return eligibleSliceReplicas;
  }

  private static List<String> transformReplicasToShardUrls(final ReplicaListTransformer replicaListTransformer, final List<Replica> eligibleSliceReplicas) {
    replicaListTransformer.transform(eligibleSliceReplicas);

    final List<String> shardUrls = new ArrayList<>(eligibleSliceReplicas.size());
    for (Replica replica : eligibleSliceReplicas) {
      String url = ZkCoreNodeProps.getCoreUrl(replica);
      shardUrls.add(url);
    }
    return shardUrls;
  }

  private static String createSliceShardsStr(final List<String> shardUrls) {
    final StringBuilder sliceShardsStr = new StringBuilder();
    boolean first = true;
    for (String shardUrl : shardUrls) {
      if (first) {
        first = false;
      } else {
        sliceShardsStr.append('|');
      }
      sliceShardsStr.append(shardUrl);
    }
    return sliceShardsStr.toString();
  }


  private void addSlices(Map<String,Slice> target, ClusterState state, SolrParams params, String collectionName, String shardKeys, boolean multiCollection) {
    DocCollection coll = state.getCollection(collectionName);
    Collection<Slice> slices = coll.getRouter().getSearchSlices(shardKeys, params , coll);
    ClientUtils.addSlices(target, collectionName, slices, multiCollection);
  }

  public ShardHandlerFactory getShardHandlerFactory(){
    return httpShardHandlerFactory;
  }



}
