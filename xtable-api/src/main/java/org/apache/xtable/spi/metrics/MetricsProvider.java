/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.apache.xtable.spi.metrics;

import org.apache.hadoop.conf.Configuration;

import org.apache.xtable.annotations.Evolving;

/**
 * Receives the outcome of every table sync so it can be reported to a metrics backend.
 * Implementations are discovered with {@link java.util.ServiceLoader}, need a public no-arg
 * constructor, and cannot fail the sync: exceptions they throw are logged and ignored.
 */
@Evolving
public interface MetricsProvider {

  default void init(Configuration conf) {}

  /** Called once per table and target format after the sync for that format has finished. */
  void onTableSynced(SyncEvent event);
}
