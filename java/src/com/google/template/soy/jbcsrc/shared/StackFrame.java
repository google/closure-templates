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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.jbcsrc.api.RenderResult;
import javax.annotation.Nullable;

/**
 * Represents a detached stack frame.
 *
 * <p>For trivial stack frames (no state is saved/restored) this class will be used directly, for
 * non-trivial ones a subclass will be generated on the fly by {@link SaveStateMetaFactory}.
 */
public class StackFrame {

  public static final StackFrame LIMITED = create(RenderResult.limited());

  public static StackFrame create(StackFrame child, int stateNumber) {
    return new StackFrame(child, stateNumber);
  }

  public static StackFrame create(RenderResult result) {
    return new StackFrame(result, 0);
  }

  public static StackFrame create(RenderResult result, int stateNumber) {
    return new StackFrame(result, stateNumber);
  }

  /** The logical position in the suspended frame. A non-negative number. */
  public final int stateNumber;

  /** The child frame in the stack. */
  @Nullable public final StackFrame child;

  // When null, this is held by the child.
  @Nullable private final RenderResult result;

  protected StackFrame(StackFrame child, int stateNumber) {
    this.child = checkNotNull(child);
    this.stateNumber = stateNumber;
    this.result = null;
  }

  protected StackFrame(RenderResult renderResult, int stateNumber) {
    this.child = null;
    this.stateNumber = stateNumber;
    this.result = checkNotNull(renderResult);
    checkArgument(!renderResult.isDone());
  }

  /** Returns the `RenderResult` that this was constructed with originally. */
  public RenderResult asRenderResult() {
    var cur = this;
    StackFrame child;
    // Find the leaf frame which always has the result.
    while ((child = cur.child) != null) {
      cur = child;
    }
    return cur.result;
  }
}
