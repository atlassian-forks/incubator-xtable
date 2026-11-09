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
 
package org.apache.xtable.utilities.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

import org.apache.xtable.model.sync.SyncMode;
import org.apache.xtable.spi.metrics.SyncEvent;

class TestStatsdMetricsProvider {

  @Test
  void sendsCounterAndGaugeToAgent() throws IOException {
    try (DatagramSocket agent = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
      Configuration conf = new Configuration(false);
      conf.set(StatsdMetricsProvider.HOST_KEY, "127.0.0.1");
      conf.setInt(StatsdMetricsProvider.PORT_KEY, agent.getLocalPort());
      conf.set(StatsdMetricsProvider.TAGS_KEY, "env:prod");
      StatsdMetricsProvider provider = new StatsdMetricsProvider();
      provider.init(conf);

      provider.onTableSynced(
          SyncEvent.builder()
              .tableName("orders")
              .format("DELTA")
              .mode(SyncMode.INCREMENTAL)
              .status("SUCCESS")
              .targetCommits(1)
              .build());

      assertEquals(
          "xtable.table_sync.result:1|c|#env:prod,table:orders,format:DELTA,"
              + "mode:INCREMENTAL,status:SUCCESS",
          receive(agent));
      assertEquals(
          "xtable.table_sync.target_commits:1|g|#env:prod,table:orders,format:DELTA",
          receive(agent));
    }
  }

  private static String receive(DatagramSocket agent) throws IOException {
    agent.setSoTimeout(5_000);
    DatagramPacket packet = new DatagramPacket(new byte[512], 512);
    agent.receive(packet);
    return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
  }
}
