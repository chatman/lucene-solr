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

public interface ReplicaStateProvider {

  Replica.State getState(Replica replica, boolean forceFetch);

  String  getLeader(Slice slice, boolean forceFetch);

  boolean isActive(Replica replica, boolean forceFetch);

  /** Invalidate the local cache
   * @param coll cannot be null. the name of the collection (if shard == null invalidate the state.json cache)
   * @param shard name of the shard. Invalidate the state of shard terms
   */
  void invalidate(String coll, String shard, int expected);

}
