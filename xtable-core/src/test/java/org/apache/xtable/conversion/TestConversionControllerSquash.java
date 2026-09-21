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
 
package org.apache.xtable.conversion;

import static org.apache.xtable.model.storage.TableFormat.DELTA;
import static org.apache.xtable.model.storage.TableFormat.ICEBERG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.apache.xtable.catalog.CatalogConversionFactory;
import org.apache.xtable.model.CommitsBacklog;
import org.apache.xtable.model.IncrementalTableChanges;
import org.apache.xtable.model.InstantsForIncrementalSync;
import org.apache.xtable.model.InternalTable;
import org.apache.xtable.model.TableChange;
import org.apache.xtable.model.metadata.TableSyncMetadata;
import org.apache.xtable.model.storage.InternalDataFile;
import org.apache.xtable.model.storage.InternalFile;
import org.apache.xtable.model.storage.InternalFilesDiff;
import org.apache.xtable.model.sync.SyncMode;
import org.apache.xtable.model.sync.SyncResult;
import org.apache.xtable.spi.extractor.ConversionSource;
import org.apache.xtable.spi.sync.CatalogSync;
import org.apache.xtable.spi.sync.ConversionTarget;
import org.apache.xtable.spi.sync.TableFormatSync;

/**
 * Verifies that {@code squashIncrementalCommits} controls whether the backlog reaching {@link
 * TableFormatSync#syncChanges} is folded. This is the seam the option is applied at, so it is
 * checked here rather than only in the pure fold test.
 */
class TestConversionControllerSquash {

  private final Configuration mockConf = mock(Configuration.class);
  private final ConversionSourceProvider<Instant> mockConversionSourceProvider =
      mock(ConversionSourceProvider.class);
  private final ConversionSource<Instant> mockConversionSource = mock(ConversionSource.class);
  private final ConversionTargetFactory mockConversionTargetFactory =
      mock(ConversionTargetFactory.class);
  private final CatalogConversionFactory mockCatalogConversionFactory =
      mock(CatalogConversionFactory.class);
  private final TableFormatSync mockTableFormatSync = mock(TableFormatSync.class);
  private final CatalogSync mockCatalogSync = mock(CatalogSync.class);
  private final ConversionTarget mockConversionTarget = mock(ConversionTarget.class);

  @BeforeEach
  void setUp() {
    when(mockConversionTarget.getTableFormat()).thenReturn(DELTA);
  }

  @Test
  void squashEnabledFoldsBacklogIntoOneChange() {
    List<TableChange> observed = runSyncAndCaptureBacklog(true);

    assertEquals(1, observed.size(), "the backlog must reach syncChanges as a single change");
    TableChange squashed = observed.get(0);
    assertEquals(
        pathsOf("b.parquet", "c.parquet"), addedPaths(squashed), "net added across the backlog");
    assertEquals(Collections.emptySet(), removedPaths(squashed), "a.parquet never became visible");
  }

  @Test
  void squashDisabledLeavesBacklogUntouched() {
    List<TableChange> observed = runSyncAndCaptureBacklog(false);

    assertEquals(3, observed.size(), "default behaviour is one target commit per source commit");
    assertEquals(pathsOf("a.parquet"), addedPaths(observed.get(0)));
    assertEquals(pathsOf("b.parquet"), addedPaths(observed.get(1)));
    assertEquals(pathsOf("a.parquet"), removedPaths(observed.get(1)));
    assertEquals(pathsOf("c.parquet"), addedPaths(observed.get(2)));
  }

  /**
   * Drives a full incremental sync with a backlog of three commits and returns the changes that
   * {@link TableFormatSync#syncChanges} actually received.
   */
  private List<TableChange> runSyncAndCaptureBacklog(boolean squashIncrementalCommits) {
    Instant lastSynced = Instant.now().minus(Duration.ofMinutes(30));
    Instant t1 = Instant.now().minus(Duration.ofMinutes(20));
    Instant t2 = Instant.now().minus(Duration.ofMinutes(10));
    Instant t3 = Instant.now().minus(Duration.ofMinutes(5));

    ConversionConfig config = config(squashIncrementalCommits);
    when(mockConversionSourceProvider.getConversionSourceInstance(config.getSourceTable()))
        .thenReturn(mockConversionSource);
    when(mockConversionTargetFactory.createForFormat(config.getTargetTables().get(0), mockConf))
        .thenReturn(mockConversionTarget);
    TableSyncMetadata metadata =
        TableSyncMetadata.of(lastSynced, Collections.emptyList(), ICEBERG, "0");
    when(mockConversionTarget.getTableMetadata()).thenReturn(Optional.of(metadata));
    when(mockConversionSource.isIncrementalSyncSafeFrom(eq(lastSynced))).thenReturn(true);
    when(mockConversionSource.getCommitsBacklog(any(InstantsForIncrementalSync.class)))
        .thenReturn(
            CommitsBacklog.<Instant>builder().commitsToProcess(Arrays.asList(t1, t2, t3)).build());
    when(mockConversionSource.getTableChangeForCommit(t1))
        .thenReturn(change(t1, "1", files("a.parquet"), Collections.emptyList()));
    when(mockConversionSource.getTableChangeForCommit(t2))
        .thenReturn(change(t2, "2", files("b.parquet"), files("a.parquet")));
    when(mockConversionSource.getTableChangeForCommit(t3))
        .thenReturn(change(t3, "3", files("c.parquet"), Collections.emptyList()));

    ArgumentCaptor<IncrementalTableChanges> captor =
        ArgumentCaptor.forClass(IncrementalTableChanges.class);
    when(mockTableFormatSync.syncChanges(any(), captor.capture()))
        .thenReturn(
            Collections.singletonMap(
                DELTA,
                Collections.singletonList(
                    SyncResult.builder()
                        .mode(SyncMode.INCREMENTAL)
                        .lastInstantSynced(t3)
                        .syncStartTime(Instant.now())
                        .syncDuration(Duration.ZERO)
                        .tableFormatSyncStatus(SyncResult.SyncStatus.SUCCESS)
                        .build())));

    ConversionController controller =
        new ConversionController(
            mockConf,
            mockConversionTargetFactory,
            mockCatalogConversionFactory,
            mockTableFormatSync,
            mockCatalogSync);
    Map<String, SyncResult> results = controller.sync(config, mockConversionSourceProvider);
    assertSame(SyncResult.SyncStatus.SUCCESS, results.get(DELTA).getTableFormatSyncStatus());
    verify(mockTableFormatSync).syncChanges(any(), any());

    List<TableChange> observed = new ArrayList<>();
    captor.getValue().getTableChanges().forEachRemaining(observed::add);
    return observed;
  }

  private ConversionConfig config(boolean squashIncrementalCommits) {
    return ConversionConfig.builder()
        .sourceTable(
            SourceTable.builder()
                .name("table")
                .formatName(ICEBERG)
                .basePath("/tmp/doesnt/matter")
                .build())
        .targetTables(
            Collections.singletonList(
                TargetTable.builder()
                    .name("table")
                    .formatName(DELTA)
                    .basePath("/tmp/doesnt/matter")
                    .build()))
        .syncMode(SyncMode.INCREMENTAL)
        .squashIncrementalCommits(squashIncrementalCommits)
        .build();
  }

  private static TableChange change(
      Instant commitTime,
      String sourceIdentifier,
      List<InternalFile> added,
      List<InternalFile> removed) {
    return TableChange.builder()
        .tableAsOfChange(InternalTable.builder().name("table").latestCommitTime(commitTime).build())
        .sourceIdentifier(sourceIdentifier)
        .filesDiff(InternalFilesDiff.builder().filesAdded(added).filesRemoved(removed).build())
        .build();
  }

  private static List<InternalFile> files(String... paths) {
    return Arrays.stream(paths)
        .map(path -> (InternalFile) InternalDataFile.builder().physicalPath(path).build())
        .collect(Collectors.toList());
  }

  private static Set<String> pathsOf(String... paths) {
    return Arrays.stream(paths).collect(Collectors.toSet());
  }

  private static Set<String> addedPaths(TableChange change) {
    return change.getFilesDiff().getFilesAdded().stream()
        .map(InternalFile::getPhysicalPath)
        .collect(Collectors.toSet());
  }

  private static Set<String> removedPaths(TableChange change) {
    return change.getFilesDiff().getFilesRemoved().stream()
        .map(InternalFile::getPhysicalPath)
        .collect(Collectors.toSet());
  }
}
