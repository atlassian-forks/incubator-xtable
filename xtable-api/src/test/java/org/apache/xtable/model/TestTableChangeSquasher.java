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
 
package org.apache.xtable.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.xtable.model.schema.InternalField;
import org.apache.xtable.model.schema.InternalPartitionField;
import org.apache.xtable.model.schema.InternalSchema;
import org.apache.xtable.model.schema.InternalType;
import org.apache.xtable.model.schema.PartitionTransformType;
import org.apache.xtable.model.storage.InternalDataFile;
import org.apache.xtable.model.storage.InternalFile;
import org.apache.xtable.model.storage.InternalFilesDiff;

/**
 * Cases U1-U7 of the squash prototype. U3 is the correctness trap: a file added and then removed
 * inside the same backlog must appear in neither the added nor the removed set, otherwise the
 * target table would carry an `add` for a data file that no longer exists.
 */
public class TestTableChangeSquasher {

  private static final Instant T1 = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant T2 = Instant.parse("2024-01-01T01:00:00Z");
  private static final Instant T3 = Instant.parse("2024-01-01T02:00:00Z");

  /** U1: a backlog of two appends folds to one change; added is the union, removed is empty. */
  @Test
  void u1_backlogOfTwoAppends() {
    TableChange c1 = change(T1, "c1", added("a.parquet"), Collections.emptyList());
    TableChange c2 = change(T2, "c2", added("b.parquet"), Collections.emptyList());

    List<TableChange> squashed = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator());

    assertEquals(1, squashed.size());
    TableChange result = squashed.get(0);
    assertEquals(paths("a.parquet", "b.parquet"), addedPaths(result));
    assertEquals(Collections.emptySet(), removedPaths(result));
    // head state wins
    assertEquals(T2, result.getTableAsOfChange().getLatestCommitTime());
    assertEquals("c2", result.getSourceIdentifier());
  }

  /** U2: a backlog of one is returned unchanged — same instance, so behaviour cannot drift. */
  @Test
  void u2_backlogOfOneIsUntouched() {
    TableChange c1 = change(T1, "c1", added("a.parquet"), removed("old.parquet"));

    List<TableChange> squashed =
        TableChangeSquasher.squash(Collections.singletonList(c1).iterator());

    assertEquals(1, squashed.size());
    assertSame(c1, squashed.get(0));
  }

  /**
   * U3: a file added in c1 and removed in c2 (compaction inside the batch) must appear in NEITHER
   * added nor removed.
   */
  @Test
  void u3_fileAddedThenRemovedWithinBatchDisappears() {
    TableChange c1 =
        change(T1, "c1", added("small1.parquet", "small2.parquet"), Collections.emptyList());
    TableChange c2 =
        change(T2, "c2", added("compacted.parquet"), removed("small1.parquet", "small2.parquet"));

    List<TableChange> squashed = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator());

    TableChange result = squashed.get(0);
    assertEquals(paths("compacted.parquet"), addedPaths(result));
    assertEquals(Collections.emptySet(), removedPaths(result));
    assertTrue(
        !addedPaths(result).contains("small1.parquet")
            && !addedPaths(result).contains("small2.parquet"),
        "intra-batch files must not be added");
    assertTrue(
        !removedPaths(result).contains("small1.parquet")
            && !removedPaths(result).contains("small2.parquet"),
        "intra-batch files were never visible to the target, so they must not be removed");
  }

  /** U4: a file removed in c1 that pre-existed the batch appears in removed only. */
  @Test
  void u4_preExistingFileRemovedAppearsInRemovedOnly() {
    TableChange c1 = change(T1, "c1", Collections.emptyList(), removed("pre-existing.parquet"));
    TableChange c2 = change(T2, "c2", added("new.parquet"), Collections.emptyList());

    List<TableChange> squashed = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator());

    TableChange result = squashed.get(0);
    assertEquals(paths("new.parquet"), addedPaths(result));
    assertEquals(paths("pre-existing.parquet"), removedPaths(result));
  }

  /** U5: schema evolution across the batch — the result carries the HEAD schema. */
  @Test
  void u5_headSchemaWins() {
    InternalSchema firstSchema = schema("id");
    InternalSchema headSchema = schema("id", "name");
    TableChange c1 =
        change(
            tableState(T1, firstSchema, Collections.emptyList()),
            "c1",
            added("a.parquet"),
            Collections.emptyList());
    TableChange c2 =
        change(
            tableState(T2, headSchema, Collections.emptyList()),
            "c2",
            added("b.parquet"),
            Collections.emptyList());

    List<TableChange> squashed = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator());

    assertEquals(headSchema, squashed.get(0).getTableAsOfChange().getReadSchema());
  }

  /** U6: partition spec evolution across the batch — the result carries the HEAD spec. */
  @Test
  void u6_headPartitionSpecWins() {
    List<InternalPartitionField> firstSpec = Collections.singletonList(partitionField("day"));
    List<InternalPartitionField> headSpec =
        Arrays.asList(partitionField("day"), partitionField("region"));
    TableChange c1 =
        change(
            tableState(T1, schema("id"), firstSpec),
            "c1",
            added("a.parquet"),
            Collections.emptyList());
    TableChange c2 =
        change(
            tableState(T2, schema("id"), headSpec),
            "c2",
            added("b.parquet"),
            Collections.emptyList());

    List<TableChange> squashed = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator());

    assertEquals(headSpec, squashed.get(0).getTableAsOfChange().getPartitioningFields());
  }

  /**
   * U7: with the option off the backlog must be handed to the sync loop untouched — same changes,
   * in the same order, as the same instances.
   */
  @Test
  void u7_optionOffLeavesBacklogUntouched() {
    TableChange c1 = change(T1, "c1", added("a.parquet"), Collections.emptyList());
    TableChange c2 = change(T2, "c2", added("b.parquet"), removed("a.parquet"));
    TableChange c3 = change(T3, "c3", added("c.parquet"), Collections.emptyList());
    List<TableChange> backlog = Arrays.asList(c1, c2, c3);

    // The squash is applied only when the option is set; this asserts the un-squashed contract that
    // ConversionController relies on when the option is off.
    IncrementalTableChanges unchanged =
        IncrementalTableChanges.builder()
            .tableChanges(backlog.iterator())
            .pendingCommits(Collections.singletonList(T1))
            .build();

    List<TableChange> observed = drain(unchanged);
    assertEquals(3, observed.size());
    assertSame(c1, observed.get(0));
    assertSame(c2, observed.get(1));
    assertSame(c3, observed.get(2));

    // And with the option on, the same backlog collapses to one change.
    IncrementalTableChanges squashed =
        TableChangeSquasher.squash(
            IncrementalTableChanges.builder()
                .tableChanges(backlog.iterator())
                .pendingCommits(Collections.singletonList(T1))
                .build());
    List<TableChange> squashedChanges = drain(squashed);
    assertEquals(1, squashedChanges.size());
    assertEquals(paths("b.parquet", "c.parquet"), addedPaths(squashedChanges.get(0)));
    assertEquals(Collections.emptySet(), removedPaths(squashedChanges.get(0)));
    assertEquals(Collections.singletonList(T1), squashed.getPendingCommits());
  }

  /** An empty backlog squashes to nothing rather than to a bogus empty commit. */
  @Test
  void emptyBacklogProducesNoChange() {
    assertEquals(
        Collections.emptyList(),
        TableChangeSquasher.squash(Collections.<TableChange>emptyList().iterator()));
  }

  /**
   * A path removed and then re-added within the batch stays added and is not reported as removed.
   */
  @Test
  void pathRemovedThenReAddedStaysAdded() {
    TableChange c1 = change(T1, "c1", Collections.emptyList(), removed("p.parquet"));
    TableChange c2 = change(T2, "c2", added("p.parquet"), Collections.emptyList());

    TableChange result = TableChangeSquasher.squash(Arrays.asList(c1, c2).iterator()).get(0);

    assertEquals(paths("p.parquet"), addedPaths(result));
    assertEquals(Collections.emptySet(), removedPaths(result));
  }

  private static List<TableChange> drain(IncrementalTableChanges changes) {
    List<TableChange> result = new java.util.ArrayList<>();
    changes.getTableChanges().forEachRemaining(result::add);
    return result;
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

  private static Set<String> paths(String... paths) {
    return Arrays.stream(paths).collect(Collectors.toSet());
  }

  private static List<InternalFile> added(String... paths) {
    return files(paths);
  }

  private static List<InternalFile> removed(String... paths) {
    return files(paths);
  }

  private static List<InternalFile> files(String... paths) {
    return Arrays.stream(paths)
        .map(path -> (InternalFile) InternalDataFile.builder().physicalPath(path).build())
        .collect(Collectors.toList());
  }

  private static TableChange change(
      Instant commitTime,
      String sourceIdentifier,
      List<InternalFile> filesAdded,
      List<InternalFile> filesRemoved) {
    return change(
        tableState(commitTime, schema("id"), Collections.emptyList()),
        sourceIdentifier,
        filesAdded,
        filesRemoved);
  }

  private static TableChange change(
      InternalTable tableState,
      String sourceIdentifier,
      List<InternalFile> filesAdded,
      List<InternalFile> filesRemoved) {
    return TableChange.builder()
        .tableAsOfChange(tableState)
        .sourceIdentifier(sourceIdentifier)
        .filesDiff(
            InternalFilesDiff.builder().filesAdded(filesAdded).filesRemoved(filesRemoved).build())
        .build();
  }

  private static InternalTable tableState(
      Instant commitTime, InternalSchema readSchema, List<InternalPartitionField> partitionFields) {
    return InternalTable.builder()
        .name("table")
        .latestCommitTime(commitTime)
        .readSchema(readSchema)
        .partitioningFields(partitionFields)
        .build();
  }

  private static InternalSchema schema(String... fieldNames) {
    return InternalSchema.builder()
        .name("record")
        .dataType(InternalType.RECORD)
        .fields(
            Arrays.stream(fieldNames)
                .map(
                    name ->
                        InternalField.builder()
                            .name(name)
                            .schema(
                                InternalSchema.builder()
                                    .name(name)
                                    .dataType(InternalType.STRING)
                                    .build())
                            .build())
                .collect(Collectors.toList()))
        .build();
  }

  private static InternalPartitionField partitionField(String name) {
    return InternalPartitionField.builder()
        .sourceField(
            InternalField.builder()
                .name(name)
                .schema(InternalSchema.builder().name(name).dataType(InternalType.STRING).build())
                .build())
        .transformType(PartitionTransformType.VALUE)
        .build();
  }
}
