/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.template.soy.parsepasses.contextautoesc.SlicedRawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;

/**
 * All context needed to perform conformance checks. Most conformance checks will only need to
 * inspect the {@link #getSoyTree syntax tree}. Conformance checks dealing specifically with the
 * HTML structure contained in the Soy template will also need to inspect the {@link
 * #getSlicedRawTextNodes raw text nodes}.
 */
@AutoValue
public abstract class ConformanceInput {

  public static ConformanceInput create(
      SoyFileSetNode soyTree, ImmutableList<SlicedRawTextNode> slicedRawTextNodes) {
    ImmutableListMultimap.Builder<String, SlicedRawTextNode> byFile =
        ImmutableListMultimap.builder();
    for (SlicedRawTextNode node : slicedRawTextNodes) {
      byFile.put(node.getRawTextNode().getSourceLocation().getFilePath(), node);
    }
    return new AutoValue_ConformanceInput(soyTree, byFile.build());
  }

  public abstract SoyFileSetNode getSoyTree();

  public abstract ImmutableListMultimap<String, SlicedRawTextNode> getSlicedRawTextNodesByFile();
}
