/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.data;

import com.google.template.soy.jbcsrc.shared.StackFrame;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Collections;

/** A lazy call. */
public class NodeBuilder {

  /**
   * Provides a way for this class to override softLimitReached. For use when replaying
   * NodeBuilders, we don't want to detach for LIMITED since we won't gencode detaches when
   * re-resolving any SoyValueProvider. Independent of the actual appendable being used.
   */
  public interface SoftLimitOverrider {
    void pushSoftLimitReachedOverride();

    void popSoftLimitReachedOverride();
  }

  /** Factory bound to a CallSite. */
  @SuppressWarnings("AvoidObjectArrays")
  public static interface Builder {
    public NodeBuilder build(Object[] templateParams, Object renderContext);
  }

  public static Builder builder(CallSite callSite) {
    return (Object[] templateParams, Object renderContext) ->
        new NodeBuilder(callSite, templateParams, renderContext);
  }

  private final CallSite callSite;
  private final Object[] templateParams;
  // Type is always com.google.template.soy.jbcsrc.shared.RenderContext, declared as Object to avoid
  // circular dep.
  private final Object renderContext;

  @SuppressWarnings("AvoidObjectArrays")
  public NodeBuilder(CallSite callSite, Object[] templateParams, Object renderContext) {
    this.callSite = callSite;
    this.templateParams = templateParams;
    this.renderContext = renderContext;
  }

  public StackFrame render(LoggingAdvisingAppendable appendable, StackFrame stackFrame) {
    ArrayList<Object> params = new ArrayList<>();
    params.add(stackFrame);
    Collections.addAll(params, templateParams);
    params.add(appendable);
    params.add(renderContext);
    try {
      return (StackFrame) callSite.getTarget().invokeWithArguments(params);
    } catch (Throwable e) {
      throw new IllegalArgumentException("Unexpected error while calling " + callSite, e);
    }
  }

  public void renderBlocking(LoggingAdvisingAppendable appendable) {
    StackFrame stackFrame = null;
    do {
      try {
        ((SoftLimitOverrider) renderContext).pushSoftLimitReachedOverride();
        stackFrame = appendable.appendNodeBuilder(this, stackFrame);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } finally {
        ((SoftLimitOverrider) renderContext).popSoftLimitReachedOverride();
      }
      if (stackFrame == null) {
        return;
      }
      stackFrame.asRenderResult().resolveDetach();
    } while (true);
  }
}
