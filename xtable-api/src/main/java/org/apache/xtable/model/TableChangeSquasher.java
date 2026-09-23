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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.xtable.model.storage.InternalFile;
import org.apache.xtable.model.storage.InternalFilesDiff;

/**
 * Folds a backlog of consecutive {@link TableChange}s into a single {@link TableChange} that
 * carries the net file diff and the head table state.
 *
 * <p>An incremental sync spanning N source commits normally emits N target commits, one per source
 * commit, because {@code TableFormatSync#syncChanges} loops over the backlog. When a source engine
 * publishes a logical batch as multiple commits, those intermediate commits become customer-visible
 * in the target table. Squashing the backlog collapses the batch into one target commit that
 * represents only the head state.
 *
 * <p>The fold, applied in commit order, is:
 *
 * <pre>
 *   net_added   = (net_added \ change.filesRemoved) u change.filesAdded
 *   net_removed = net_removed u (change.filesRemoved \ net_added_so_far)
 *   tableAsOfChange = the LAST change's table state (head schema, partition spec, metadata)
 * </pre>
 *
 * <p>Files are keyed by {@link InternalFile#getPhysicalPath()}. The first clause is the correctness
 * critical one: a file added earlier in the batch and removed later in the same batch (compaction
 * inside the batch) must appear in neither the added nor the removed set, otherwise the target
 * would reference a data file that no longer exists.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TableChangeSquasher {

  /**
   * Squashes the given changes, in iteration (commit) order, into at most one {@link TableChange}.
   *
   * @param changes the backlog of changes in commit order
   * @return an empty list when the backlog was empty, otherwise a single element list holding the
   *     squashed change
   */
  public static List<TableChange> squash(Iterator<TableChange> changes) {
    // Keyed by physical path so that repeated observations of the same file collapse. LinkedHashMap
    // keeps the resulting sets deterministic in commit order, which keeps target commits stable.
    Map<String, InternalFile> netAdded = new LinkedHashMap<>();
    Map<String, InternalFile> netRemoved = new LinkedHashMap<>();
    TableChange lastChange = null;
    int count = 0;

    while (changes.hasNext()) {
      TableChange change = changes.next();
      lastChange = change;
      count++;
      InternalFilesDiff diff = change.getFilesDiff();
      if (diff == null) {
        continue;
      }
      for (InternalFile removed : nullSafe(diff.getFilesRemoved())) {
        String path = removed.getPhysicalPath();
        // A file added earlier within this same batch and now removed never existed as far as the
        // target is concerned: drop it from the added set and do not report it as removed.
        if (netAdded.remove(path) == null) {
          netRemoved.put(path, removed);
        }
      }
      for (InternalFile added : nullSafe(diff.getFilesAdded())) {
        String path = added.getPhysicalPath();
        // A path re-added after having been removed in this batch is a net no-op for removal, but
        // it
        // is still the latest observation of the file, so it must be present in the added set.
        netRemoved.remove(path);
        netAdded.put(path, added);
      }
    }

    if (lastChange == null) {
      log.info("Squash: empty source backlog, no target commit will be emitted.");
      return Collections.emptyList();
    }
    if (count == 1) {
      // Nothing to fold; return the original instance so behaviour is identical to not squashing.
      log.info(
          "Squash: backlog held 1 source commit, nothing to fold; emitting it unchanged. "
              + "Enabling squashIncrementalCommits has no observable effect on a single-commit "
              + "backlog.");
      return Collections.singletonList(lastChange);
    }
    InternalFilesDiff squashedDiff =
        InternalFilesDiff.builder()
            .filesAdded(new ArrayList<>(netAdded.values()))
            .filesRemoved(new ArrayList<>(netRemoved.values()))
            .build();
    log.info(
        "Squashed {} source commits into 1 target commit: net filesAdded={}, filesRemoved={}, "
            + "head table state from source commit at {}.",
        count,
        netAdded.size(),
        netRemoved.size(),
        lastChange.getTableAsOfChange() == null
            ? "unknown"
            : lastChange.getTableAsOfChange().getLatestCommitTime());
    return Collections.singletonList(lastChange.toBuilder().filesDiff(squashedDiff).build());
  }

  /** Squashes the changes of an {@link IncrementalTableChanges}, preserving pending commits. */
  public static IncrementalTableChanges squash(IncrementalTableChanges changes) {
    return IncrementalTableChanges.builder()
        .tableChanges(squash(changes.getTableChanges()).iterator())
        .pendingCommits(changes.getPendingCommits())
        .build();
  }

  private static <T> Iterable<T> nullSafe(Iterable<T> values) {
    return values == null ? Collections.emptyList() : values;
  }
}
