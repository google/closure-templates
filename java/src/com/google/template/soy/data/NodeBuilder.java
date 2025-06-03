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

import com.google.common.time.Sleeper;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.CallSite;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;

/** A lazy call. */
public class NodeBuilder {

  /** Factory bound to a CallSite. */
  @SuppressWarnings("AvoidObjectArrays")
  public static interface Builder {
    public NodeBuilder build(StackFrame stackFrame, Object[] templateParams, Object renderContext);
  }

  public static Builder builder(CallSite callSite) {
    return (StackFrame stackFrame, Object[] templateParams, Object renderContext) ->
        new NodeBuilder(callSite, stackFrame, templateParams, renderContext);
  }

  private final CallSite callSite;
  private final StackFrame stackFrame;
  private final Object[] templateParams;
  // Type is always com.google.template.soy.jbcsrc.shared.RenderContext, declared as Object to avoid
  // circular dep.
  private final Object renderContext;

  @SuppressWarnings("AvoidObjectArrays")
  public NodeBuilder(
      CallSite callSite, StackFrame stackFrame, Object[] templateParams, Object renderContext) {
    this.callSite = callSite;
    this.stackFrame = stackFrame;
    this.templateParams = templateParams;
    this.renderContext = renderContext;
  }

  public StackFrame render(LoggingAdvisingAppendable appendable) {
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
    NodeBuilder builder = this;
    do {
      StackFrame newFrame;
      try {
        newFrame = appendable.appendNodeBuilder(builder);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      if (newFrame == null) {
        return;
      }
      RenderResult result = newFrame.asRenderResult();
      switch (result.type()) {
        case LIMITED:
          try {
            Sleeper.defaultSleeper().sleep(Duration.ofMillis(0));
          } catch (InterruptedException e) {
            // Ignore, keep going.
          }
          break;
        case DETACH:
          result.resolveDetach();
          break;
        case DONE:
          return;
      }
      // TODO(b/421209829): Should we handle LIMITED somehow? sleep?
      builder = new NodeBuilder(callSite, newFrame, templateParams, renderContext);
    } while (true);
  }
}
