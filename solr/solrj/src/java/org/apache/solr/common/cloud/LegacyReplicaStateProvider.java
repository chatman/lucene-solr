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

package org.apache.solr.common.cloud;


public class LegacyReplicaStateProvider implements ReplicaStateProvider {
  ZkStateReader stateReader;

  public LegacyReplicaStateProvider(ZkStateReader zkStateReader) {
    this.stateReader = zkStateReader;
  }

  @Override
  public Replica.State getState(Replica replica, boolean forceFetch) {
    return replica.getState();
  }

  @Override
  public boolean isActive(Replica replica, boolean forceFetch) {
    return  replica.getNodeName() != null &&
        replica.getState() == Replica.State.ACTIVE &&
        stateReader.isNodeLive(replica.getNodeName());
  }

  @Override
  public String getLeader(Slice slice, boolean forceFetch) {
    return slice.getLeader().getName();
  }

  @Override
  public void invalidate(String coll, String shard, int expected) {
    //do nothing
  }
}
