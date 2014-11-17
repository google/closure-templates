/*
 * Copyright 2014 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A stack of local variable bindings.
 * 
 * <p>Each soy template gets its own Environment instance and within that template there may be
 * multiple {@link Frame frames} of variable bindings.  For example:
 * 
 * <p>Example: <pre>   {@code
 *  
 *   {template .foo}
 *     {let $var1: 2 /}
 *     {let $var2}
 *       hello var2
 *     {/let}
 *     {for $i in range(10)}
 *       {let $var1: $i+1 /} 
 *     {/for}
 *   {/template}}</pre>
 *   
 * <p>In the above example there are 2 frames active inside of the for loop.  The first one contains
 * bindings for {@code $i} and {@code $var1}, the second one contains bindings for {@code $var1} and
 * {@code $var2}.  Variable lookup is performed recursively by walking the stack looking for 
 * bindings.
 * 
 * <p>New empty environments can be created with the {@link #create} factory method and seeded with
 * the {@link #bind} method.
 * 
 * <p>For the most part this class is only used by this package, but it is publicly exposed to aid
 * in testing usecases.
 */
public final class Environment {
  /** Creates a new Environment with a single empty frame. */
  public static Environment create() {
    return new Environment();
  }

  /** Opaque type that represents a point in time snapshot of the local variable environment. */
  static final class Snapshot {
    private NonRootFrame frame;

    private Snapshot(NonRootFrame frame) {
      this.frame = frame;
    }
  }
  
  // NOTE: We treat loop frames and variables specially since they are the only soy local variable
  // bindings that can be reset.  We could fix that by requiring each loop iteration to push/pop a
  // new frame, but that would be very expensive.

  /**
   * A special interface for reassigning the loop variable binding for a given loop iteration.
   */
  static interface ForFrame {
    void resetLoopVar(int index);
  }

  /**
   * A special interface for reassigning the loop variable binding and index for a given loop 
   * iteration.
   */
  static interface ForEachFrame {
    void resetLoopVar(SoyValueProvider value, int index);
  }

  private NonRootFrame frame = new NonRootFrame(RootFrame.INSTANCE);
  
  private Environment() {}
  
  /** Pushes a new frame. */
  void push() {
    frame = new NonRootFrame(frame);
  }

  /** 
   * Pushes and returns a new frame that can be used to update the loop variables for each iteration
   * of a for loop. 
   */
  ForFrame pushForLoop(String loopVar) {
    ForFrameImpl forFrame = new ForFrameImpl(frame, loopVar);
    frame = forFrame;
    return forFrame;
  }

  /** 
   * Pushes and returns a new frame that can be used to update the loop variable for each iteration
   * of a foreach loop.
   */
  ForEachFrame pushForEachLoop(String loopVar, int lastIndex) {
    ForEachFrameImpl forFrame = new ForEachFrameImpl(frame, loopVar, lastIndex);
    frame = forFrame;
    return forFrame;
  }

  /** Pops off the current frame.  */
  void pop() {
    Frame parent = frame.parent;
    checkState(parent instanceof NonRootFrame, "popped too many times!");
    frame = (NonRootFrame) parent;
  }
  
  /** Returns a point in time snapshot of the environment. */
  Snapshot snapshot() {
    return new Snapshot(frame);
  }
  
  /** Resets this reference to point to the Snapshot as it was at the time. */
  void reset(Snapshot snapshot) {
    this.frame = snapshot.frame;
  }

  /** Looks up a local variable by recursively searching parent frames. */
  SoyValue getLocalVar(String localVarName) {
    return frame.getLocalVar(localVarName);
  }

  /** Looks up the index of the loop variable. */
  int getIndex(String loopVarName) {
    return frame.getLoopIndex(loopVarName);
  }
  
  /** Returns true if we are at the last iteration of the loop for this loop variable. */
  boolean isLastIndex(String loopVarName) {
    return frame.isLastIteration(loopVarName);
  }

  /** Adds a new local variable binding to the environment. */
  void bind(String localVar, SoyValueProvider value) {
    SoyValueProvider oldValue = frame.set(localVar, value);
    if (oldValue != null) {
      frame.set(localVar, oldValue);  // restore state
      throw new IllegalStateException(localVar + " was already bound");
    }
  }

  /** 
   * A Frame is a collection of bindings in a particular scope of execution.
   * 
   * <p>For the most part the API is just {@link #getLocalVar}, but there are helpers for looking
   * up loop indices as well. 
   */
  private abstract static class Frame {
    /** 
     * Returns the variable bound to the given name or {@code null} if no such variable is bound. 
     */
    @Nullable abstract SoyValue getLocalVar(String localVarName);

    /** Returns the current loop index for the loop with the given variable name. */
    abstract int getLoopIndex(String loopVarName);

    /** 
     * Returns {@code true} if this is the last iteration of the loop with the given variable name. 
     */
    abstract boolean isLastIteration(String loopVarName);
  }

  /**
   * Simple root empty frame for implementing recursion base cases in our search methods.
   */
  private static final class RootFrame extends Frame {
    static final RootFrame INSTANCE = new RootFrame();

    @Override SoyValue getLocalVar(String localVarName) {
      return null;
    }

    @Override int getLoopIndex(String loopVarName) {
      throw new RuntimeException("no loop variable available for: " + loopVarName);
    }

    @Override boolean isLastIteration(String loopVarName) {
      throw new RuntimeException("no loop variable available for: " + loopVarName);
    }
  }

  /** Base non empty {@link Frame} implementation. */
  private static class NonRootFrame extends Frame {
    // TODO(lukes): consider switching to an array based format.  The local variables that are live
    // in a given set of frames should be statically verifiable so in theory instead of string based
    // lookups we could say 'give me the 3rd binding in the 2nd frame up from here'.
    
    final Frame parent;
    final Map<String, SoyValueProvider> locals = new HashMap<>();

    NonRootFrame(Frame parent) {
      this.parent = checkNotNull(parent);
    }

    @Override SoyValue getLocalVar(String localVarName) {
      Map<String, SoyValueProvider> l = locals;
      if (l != null) {
        SoyValueProvider value = l.get(localVarName);
        if (value != null) {
          return value.resolve();
        }
      }
      return parent.getLocalVar(localVarName);
    }
    
    @Override int getLoopIndex(String loopVarName) {
      return parent.getLoopIndex(loopVarName);
    }
    
    @Override boolean isLastIteration(String loopVarName) {
      return parent.isLastIteration(loopVarName);
    }
    
    /** Sets a variable binding, returning the previously set value if any. */
    SoyValueProvider set(String localVar, SoyValueProvider value) {
      return locals.put(localVar, value);
    }
  }

  /** Implementation of a {@link ForFrame} that allows resetting the loop variable. */
  private static final class ForFrameImpl extends NonRootFrame implements ForFrame {
    final String loopVarName;
    
    ForFrameImpl(NonRootFrame parent, String loopVarName) {
      super(parent);
      this.loopVarName = loopVarName;
    }

    @Override public void resetLoopVar(int value) {
      locals.clear();
      locals.put(loopVarName, IntegerData.forValue(value));
    }
  }

  /** 
   * Implementation of a {@link ForEachFrame} that allows resetting the loop and index variables.
   */
  private static final class ForEachFrameImpl extends NonRootFrame implements ForEachFrame {
    final String loopVarName;
    final int lastIndex;
    int currentIndex;

    ForEachFrameImpl(NonRootFrame parent, String loopVarName, int lastIndex) {
      super(parent);
      this.loopVarName = loopVarName;
      this.lastIndex = lastIndex;
    }
    
    @Override public void resetLoopVar(SoyValueProvider value, int index) {
      locals.clear();
      locals.put(loopVarName, value);
      this.currentIndex = index;
    }

    @Override int getLoopIndex(String loopVarName) {
      if (loopVarName.equals(this.loopVarName)) {
        return currentIndex;
      }
      return super.getLoopIndex(loopVarName);
    }

    @Override boolean isLastIteration(String loopVarName) {
      if (loopVarName.equals(this.loopVarName)) {
        return lastIndex == currentIndex;
      }
      return super.isLastIteration(loopVarName);
    }
  }
}
