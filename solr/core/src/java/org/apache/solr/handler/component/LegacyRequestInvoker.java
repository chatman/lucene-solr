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

import static org.apache.solr.handler.component.ShardRequest.PURPOSE_GET_FIELDS;

import java.util.List;
import java.util.Set;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.cloud.SolrRequestInvoker;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.apache.solr.client.solrj.impl.LBSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.SimpleSolrResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.util.tracing.SolrRequestCarrier;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class LegacyRequestInvoker implements SolrRequestInvoker {

  final private Http2SolrClient http2Client;
  final private HttpClient legacyHttpClient;
  final List<String> urls;

  final private LBHttp2SolrClient loadbalancer;
  final int permittedLoadBalancerRequestsMinimumAbsolute;
  final float permittedLoadBalancerRequestsMaximumFraction;

  public LegacyRequestInvoker(Http2SolrClient client, HttpClient legacyHttpClient, List<String> urls,
      LBHttp2SolrClient loadBalancer, int permittedLoadBalancerRequestsMinimumAbsolute, float permittedLoadBalancerRequestsMaximumFraction) {
    this.http2Client = client;
    this.legacyHttpClient = legacyHttpClient;
    this.urls = urls;
    this.loadbalancer = loadBalancer;
    this.permittedLoadBalancerRequestsMaximumFraction = permittedLoadBalancerRequestsMaximumFraction;
    this.permittedLoadBalancerRequestsMinimumAbsolute = permittedLoadBalancerRequestsMinimumAbsolute;
  }

  static final BinaryResponseParser READ_STR_AS_CHARSEQ_PARSER = new BinaryResponseParser() {
    @Override
    protected JavaBinCodec createCodec() {
      return new JavaBinCodec(null, stringCache).setReadStringAsCharSeq(true);
    }
  };
  
  @Override
  public NamedList<Object> request(Request request) throws Exception {
    System.out.println("Trying to invoke: "+request.solrRequest().getBasePath());
    //try {
      if (urls.size() > 1) {
        //request.solrRequest().setBasePath(null);
        int numServersToTry = (int)Math.floor(urls.size() * this.permittedLoadBalancerRequestsMaximumFraction);
        if (numServersToTry < this.permittedLoadBalancerRequestsMinimumAbsolute) {
          numServersToTry = this.permittedLoadBalancerRequestsMinimumAbsolute;
        }
        LBSolrClient.Rsp rsp = loadbalancer.request(new LBSolrClient.Req(request.solrRequest(), urls, numServersToTry));
        request.solrRequest().setBasePath(rsp.getServer());
        return rsp.getResponse();
      } else if (http2Client != null) {
        return http2Client.request(request.solrRequest());
      } else {
        try (SolrClient client = new HttpSolrClient.Builder(request.solrRequest().getBasePath()).withHttpClient(legacyHttpClient).build()) {
          return client.request(request.solrRequest());
        }
      } 
    //}  catch (IOException | SolrServerException e) {
      //throw new SolrException(ErrorCode.SERVER_ERROR, e);
    //}
  }
  
  public ShardResponse wrapSimpleResponseToShardResponse(final ShardRequest sreq, final String shard,
      SimpleSolrResponse ssr) {
    ShardResponse srsp = new ShardResponse();
    if (sreq.nodeName != null) {
      srsp.setNodeName(sreq.nodeName);
    }
    srsp.setShardRequest(sreq);
    srsp.setShard(shard);
    srsp.setSolrResponse(ssr);
    
    return srsp;
  }


  public Request getInvocationRequest(final ShardRequest sreq, final String shard, final ModifiableSolrParams params,
      final List<String> urls, final Tracer tracer, final Span span) {
    params.remove(CommonParams.WT); // use default (currently javabin)
    params.remove(CommonParams.VERSION);

    QueryRequest req = new QueryRequest(params);
    if (tracer != null && span != null) {
      tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new SolrRequestCarrier(req));
    }
    req.setMethod(SolrRequest.METHOD.POST);
    SolrRequestInfo requestInfo = SolrRequestInfo.getRequestInfo();
    if (requestInfo != null) req.setUserPrincipal(requestInfo.getReq().getUserPrincipal());

    if (sreq.purpose == PURPOSE_GET_FIELDS) {
      req.setResponseParser(LegacyRequestInvoker.READ_STR_AS_CHARSEQ_PARSER);
    }
    // no need to set the response parser as binary is the default
    // req.setResponseParser(new BinaryResponseParser());

    // if there are no shards available for a slice, urls.size()==0
    if (urls.size()==0) {
      // TODO: what's the right error code here? We should use the same thing when
      // all of the servers for a shard are down.
      throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE, "no servers hosting shard: " + shard);
    }

    String url = urls.get(urls.size()-1);
    System.out.println("From "+urls+", picking "+url);
    
    if (urls.size() == 1) {
      req.setBasePath(url);
    }
    Request invocationRequest = new Request() {
      @Override
      public boolean refreshForRetry(Set<String> staleCollectionStates, Set<String> staleShardTerms) {
        return false;
      }
      @Override
      public QueryRequest solrRequest() {
        return req;
      }
      @Override
      public List<StateAssumption> getStateAssumptions() {
        return null;
      }
    };
    return invocationRequest;
  }

}
