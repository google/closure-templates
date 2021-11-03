/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.internal.util.TopoSort;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Sorts the children of {@link SoyFileSetNode} into dependency order, starting with files that
 * depend on no other files in the file set. Fails on cycles. Stores the ordered list in the pass
 * manager for later use.
 */
@RunAfter(ImportsPass.class)
public class FileDependencyOrderPass implements CompilerFileSetPass {

  private static final SoyErrorKind CYCLE =
      SoyErrorKind.of("Dependency cycle between source files:\n{0}", StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;
  private final Consumer<ImmutableList<SoyFileNode>> stateSetter;

  public FileDependencyOrderPass(
      ErrorReporter errorReporter, Consumer<ImmutableList<SoyFileNode>> stateSetter) {
    this.errorReporter = errorReporter;
    this.stateSetter = stateSetter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> files, IdGenerator idGenerator) {
    if (files.size() < 2) {
      stateSetter.accept(files);
      return Result.CONTINUE;
    }

    Map<String, SoyFileNode> filesByPath =
        files.stream().collect(toImmutableMap(fn -> fn.getFilePath().path(), fn -> fn));

    TopoSort<SoyFileNode> sorter = new TopoSort<>();
    try {
      ImmutableList<SoyFileNode> sorted =
          sorter.sort(
              files,
              fn ->
                  fn.getImports().stream()
                      .map(ImportNode::getPath)
                      .map(filesByPath::get)
                      .filter(Objects::nonNull)
                      .collect(Collectors.toSet()));
      stateSetter.accept(sorted);
      return Result.CONTINUE;
    } catch (NoSuchElementException e) {
      String cycleText =
          sorter.getCyclicKeys().stream()
              .map(fn -> fn.getFilePath().path())
              .collect(joining("\n--> "));
      errorReporter.report(SourceLocation.UNKNOWN, CYCLE, cycleText);
      return Result.STOP;
    }
  }
}
