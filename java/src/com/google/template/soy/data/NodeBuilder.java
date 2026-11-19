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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
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

  /** Per-render options for lazy calls, implemented by the render context. */
  public interface Options {
    /** Whether to call templates through a cached spread invoker. */
    boolean useFastLazyCalls();
  }

  /** Factory bound to a CallSite. */
  @SuppressWarnings("AvoidObjectArrays")
  public static interface Builder {
    public NodeBuilder build(Object[] templateParams, Object renderContext);
  }

  public static Builder builder(CallSite callSite) {
    // Shared by all NodeBuilders created for this call site.
    SpreadInvoker invoker = new SpreadInvoker(callSite);
    return (Object[] templateParams, Object renderContext) ->
        new NodeBuilder(invoker, templateParams, renderContext);
  }

  /**
   * Calls the target of a call site with the template params passed as an array, so that {@link
   * #render} can use {@code invokeExact}. {@code invokeWithArguments} is much slower, since it
   * creates a spreader and adapts its type on every call.
   */
  private static final class SpreadInvoker {
    /** Must exactly match the type of the {@code invokeExact} call in {@link #renderFast}. */
    private static final MethodType TYPE =
        MethodType.methodType(
            StackFrame.class,
            StackFrame.class,
            Object[].class,
            LoggingAdvisingAppendable.class,
            Object.class);

    final CallSite callSite;
    // Lazily created and shared across threads, so volatile for safe publication.
    private volatile MethodHandle handle;

    SpreadInvoker(CallSite callSite) {
      this.callSite = callSite;
    }

    MethodHandle get() {
      MethodHandle handle = this.handle;
      if (handle == null) {
        // The target's parameters are (stackFrame, ...templateParams, appendable, renderContext),
        // so only spread the template params at position 1.
        this.handle =
            handle =
                callSite
                    .dynamicInvoker()
                    .asSpreader(1, Object[].class, callSite.type().parameterCount() - 3)
                    .asType(TYPE);
      }
      return handle;
    }
  }

  private final SpreadInvoker invoker;
  private final Object[] templateParams;
  // Type is always com.google.template.soy.jbcsrc.shared.RenderContext, declared as Object to avoid
  // circular dep.
  private final Object renderContext;

  @SuppressWarnings("AvoidObjectArrays")
  public NodeBuilder(CallSite callSite, Object[] templateParams, Object renderContext) {
    this(new SpreadInvoker(callSite), templateParams, renderContext);
  }

  @SuppressWarnings("AvoidObjectArrays")
  private NodeBuilder(SpreadInvoker invoker, Object[] templateParams, Object renderContext) {
    this.invoker = invoker;
    this.templateParams = templateParams;
    this.renderContext = renderContext;
  }

  public StackFrame render(LoggingAdvisingAppendable appendable, StackFrame stackFrame) {
    if (((Options) renderContext).useFastLazyCalls()) {
      return renderFast(appendable, stackFrame);
    }
    CallSite callSite = invoker.callSite;
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

  private StackFrame renderFast(LoggingAdvisingAppendable appendable, StackFrame stackFrame) {
    try {
      return (StackFrame)
          invoker.get().invokeExact(stackFrame, templateParams, appendable, renderContext);
    } catch (Throwable e) {
      throw new IllegalArgumentException("Unexpected error while calling " + invoker.callSite, e);
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
