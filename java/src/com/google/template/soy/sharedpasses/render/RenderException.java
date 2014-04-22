/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.TemplateNode;

import java.util.LinkedList;

/**
 * Exception thrown when a rendering or evaluation attempt fails.
 *
 */
public class RenderException extends RuntimeException {


  /** The location of the source file that triggered the exception. */
  private SourceLocation partialStackTraceElement;


  /** The list of all stack traces from the soy rendering. */
  private final LinkedList<StackTraceElement> soyStackTrace;


  /**
   * @param message A detailed description of the error.
   */
  public RenderException(String message) {
    super(message);
    this.partialStackTraceElement = null;
    this.soyStackTrace = new LinkedList<StackTraceElement>();
  }


  /**
   * @param message A detailed description of the error.
   * @param cause The underlying error.
   */
  public RenderException(String message, Throwable cause) {
    super(message, cause);
    this.partialStackTraceElement = null;
    this.soyStackTrace = new LinkedList<StackTraceElement>();
  }


  /**
   * Add a partial stack trace element by specifying the source location of the soy file.
   */
  RenderException addPartialStackTraceElement(SourceLocation srcLocation) {
    if (partialStackTraceElement != null) {
      // Somehow the previous partialStackTraceElement didn't get completely built.  Finish it.
      soyStackTrace.add(new StackTraceElement("[Unknown]", "[Unknown]",
          partialStackTraceElement.getFileName(), partialStackTraceElement.getLineNumber()));
    }
    partialStackTraceElement = srcLocation;
    return this;
  }


  /**
   * Adds a stack trace element to the current RenderException, to be used to generate a custom
   * stack trace when rendering fails.
   * @param node The TemplateNode that will provide all the information to construct a
   *     StackTraceElement.
   * @return The same instance.
   */
  RenderException completeStackTraceElement(TemplateNode node) {
    if (partialStackTraceElement == null) {
      // Somehow the render exception did not have a source location.  We have to fake it.
      soyStackTrace.add(node.createStackTraceElement(SourceLocation.UNKNOWN));
    } else {
      soyStackTrace.add(node.createStackTraceElement(partialStackTraceElement));
    }
    partialStackTraceElement = null;
    return this;
  }


  /**
   * Finalize the current stack trace by prepending the soy stack trace.
   */
  public void finalizeStackTrace() {
    finalizeStackTrace(this);
  }


  /**
   * Finalize the stack trace by prepending the soy stack trace to the given Throwable.
   */
  public void finalizeStackTrace(Throwable t) {
    t.setStackTrace(concatWithJavaStackTrace(t.getStackTrace()));
  }


  /**
   * Prepend the soy stack trace to the given standard java stack trace.
   * @param javaStackTrace The java stack trace to prepend.  This should come from
   *     Throwable#getStackTrace()
   * @return The combined stack trace to use.  Callers should call Throwable#setStackTrace() to
   *     override another Throwable's stack trace.
   */
  private StackTraceElement[] concatWithJavaStackTrace(StackTraceElement[] javaStackTrace) {
    if (soyStackTrace.isEmpty()) {
      return javaStackTrace;
    }

    StackTraceElement[] finalStackTrace =
        new StackTraceElement[soyStackTrace.size() + javaStackTrace.length];
    int i = 0;
    for (StackTraceElement soyStackTraceElement : soyStackTrace) {
      finalStackTrace[i] = soyStackTraceElement;
      i++;
    }
    System.arraycopy(
        javaStackTrace, 0, finalStackTrace, soyStackTrace.size(), javaStackTrace.length);
    return finalStackTrace;
  }
}
