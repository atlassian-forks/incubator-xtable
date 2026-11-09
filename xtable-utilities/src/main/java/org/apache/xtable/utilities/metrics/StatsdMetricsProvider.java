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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import lombok.extern.log4j.Log4j2;

import org.apache.hadoop.conf.Configuration;

import org.apache.xtable.spi.metrics.MetricsProvider;
import org.apache.xtable.spi.metrics.SyncEvent;

/**
 * Sends {@code xtable.table_sync.result} (counter) and {@code xtable.table_sync.target_commits}
 * (gauge) to a StatsD agent over UDP in DogStatsD format. Enabled only when {@value #HOST_KEY} is
 * set in the configuration; {@value #PORT_KEY} defaults to 8125 and {@value #TAGS_KEY} (e.g.
 * {@code env:prod,region:us-west-2}) is appended to every metric.
 */
@Log4j2
public class StatsdMetricsProvider implements MetricsProvider {
  static final String HOST_KEY = "xtable.metrics.statsd.host";
  static final String PORT_KEY = "xtable.metrics.statsd.port";
  static final String TAGS_KEY = "xtable.metrics.statsd.tags";

  private DatagramSocket socket;
  private InetSocketAddress agent;
  private String tags;

  @Override
  public void init(Configuration conf) {
    String host = conf.get(HOST_KEY);
    if (host == null) {
      return;
    }
    try {
      agent = new InetSocketAddress(host, conf.getInt(PORT_KEY, 8125));
      socket = new DatagramSocket();
      tags = conf.get(TAGS_KEY, "");
      log.info("StatsD metrics enabled, sending to {}", agent);
    } catch (Exception e) {
      log.warn("StatsD metrics disabled: {}", e.getMessage());
    }
  }

  @Override
  public void onTableSynced(SyncEvent event) {
    if (socket == null) {
      return;
    }
    String base =
        (tags.isEmpty() ? "" : tags + ",")
            + "table:" + clean(event.getTableName())
            + ",format:" + event.getFormat();
    send("xtable.table_sync.result:1|c|#" + base
        + ",mode:" + event.getMode() + ",status:" + event.getStatus());
    send("xtable.table_sync.target_commits:" + event.getTargetCommits() + "|g|#" + base);
  }


  private static String clean(String value) {
    return value.replaceAll("[,|#:\\s]", "_");
  }

  private void send(String line) {
    byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
    try {
      socket.send(new DatagramPacket(bytes, bytes.length, agent));
    } catch (IOException e) {
      log.warn("Failed to send StatsD metric {}", line, e);
    }
  }
}
