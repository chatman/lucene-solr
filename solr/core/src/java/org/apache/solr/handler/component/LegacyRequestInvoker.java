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

import java.io.IOException;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.cloud.SolrRequestInvoker;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.apache.solr.client.solrj.impl.LBSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.common.util.NamedList;

public class LegacyRequestInvoker implements SolrRequestInvoker {

  final private Http2SolrClient http2Client;
  final private HttpClient legacyHttpClient;
  final private LBHttp2SolrClient loadBalancerClient;
  final private int permittedLoadBalancerRequestsMinimumAbsolute;
  final private float permittedLoadBalancerRequestsMaximumFraction;
  
  public LegacyRequestInvoker(Http2SolrClient client, HttpClient legacyHttpClient,
      LBHttp2SolrClient loadBalancer, int permittedLoadBalancerRequestsMinimumAbsolute, float permittedLoadBalancerRequestsMaximumFraction) {
    this.http2Client = client;
    this.legacyHttpClient = legacyHttpClient;
    this.loadBalancerClient = loadBalancer;
    this.permittedLoadBalancerRequestsMinimumAbsolute = permittedLoadBalancerRequestsMinimumAbsolute;
    this.permittedLoadBalancerRequestsMaximumFraction = permittedLoadBalancerRequestsMaximumFraction;
  }
  
  static final BinaryResponseParser READ_STR_AS_CHARSEQ_PARSER = new BinaryResponseParser() {
    @Override
    protected JavaBinCodec createCodec() {
      return new JavaBinCodec(null, stringCache).setReadStringAsCharSeq(true);
    }
  };
  
  protected LBSolrClient.Req newLBHttpSolrClientReq(final QueryRequest req, List<String> urls) {
    int numServersToTry = (int)Math.floor(urls.size() * permittedLoadBalancerRequestsMaximumFraction);
    if (numServersToTry < permittedLoadBalancerRequestsMinimumAbsolute) {
      numServersToTry = permittedLoadBalancerRequestsMinimumAbsolute;
    }
    return new LBSolrClient.Req(req, urls, numServersToTry);
  }

  @Override
  public NamedList<Object> request(Request request) throws SolrException {
    try {
      if (http2Client == null) {
        try (SolrClient client = new HttpSolrClient.Builder(request.solrRequest().getBasePath()).withHttpClient(legacyHttpClient).build()) {
          return client.request(request.solrRequest());
        }
      } else {
        return http2Client.request(request.solrRequest());
      }
    }  catch (IOException | SolrServerException e) {
      throw new SolrException(ErrorCode.SERVER_ERROR, e);
    }
  }

}
