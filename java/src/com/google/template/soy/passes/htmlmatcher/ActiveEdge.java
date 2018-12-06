/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.passes.htmlmatcher;

import com.google.auto.value.AutoValue;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;

/**
 * Holds a node and its active edge for later accumulation into an {@link
 * HtmlMatcherAccumulatorNode}.
 */
@AutoValue
public abstract class ActiveEdge {
  public abstract HtmlMatcherGraphNode getGraphNode();

  public abstract EdgeKind getActiveEdge();

  public static ActiveEdge create(HtmlMatcherGraphNode graphNode, EdgeKind activeEdge) {
    return new AutoValue_ActiveEdge(graphNode, activeEdge);
  }
}
