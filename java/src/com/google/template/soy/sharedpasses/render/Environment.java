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


import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarDefn.Kind;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.IdentityHashMap;

/**
 * The local variable table.
 *
 * <p>All declared {@code @param}s and {@code {let ...}} statements define variables that are stored
 * in a table. The mapping between local variable and
 *
 * <p>New empty environments can be created with the {@link #create} factory method and seeded with
 * the {@link #bind} method.
 *
 * <p>For the most part this class is only used by this package, but it is publicly exposed to aid
 * in testing usecases.
 */
public abstract class Environment {
  Environment() {} // package private constructor to limit subclasses to this package.

  /**
   * The main way to create an environment.
   *
   * <p>Allocates the local variable table for the template and prepopulates it with data from the
   * given SoyRecords.
   */
  static Environment create(TemplateNode template, SoyRecord data, SoyRecord ijData) {
    return new Impl(template, data, ijData);
  }

  /**
   * For Prerendering we create an {@link Environment} for the given template where all entries are
   * initialized to UndefinedData.
   */
  public static Environment prerenderingEnvironment() {
    return new EmptyImpl();
  }

  /** Associates a value with the given variable. */
  abstract void bind(VarDefn var, SoyValueProvider value);

  /**
   * Binds the data about the current loop position to support the isLast and index builtin
   * functions.
   */
  abstract void bindLoopPosition(
      VarDefn loopVar, SoyValueProvider value, int index, boolean isLast);

  /** Returns the resolved SoyValue for the given VarDefn. Guaranteed to not return null. */
  abstract SoyValue getVar(VarDefn var);

  /** Returns the resolved SoyValue for the given VarDefn. Guaranteed to not return null. */
  abstract SoyValueProvider getVarProvider(VarDefn var);

  /** Returns {@code true} if we are the last iteration for the given loop variable. */
  abstract boolean isLast(VarDefn loopVar);

  /** Returns the current iterator inject for the given loop variable. */
  abstract int getIndex(VarDefn loopVar);

  private static final class Impl extends Environment {
    private static final class LoopPosition {
      boolean isLast;
      int index;
      SoyValueProvider item;
    }

    final IdentityHashMap<VarDefn, Object> localVariables = new IdentityHashMap<>();
    final SoyRecord data;
    Impl(TemplateNode template, SoyRecord data, SoyRecord ijData) {
      this.data = data;
      for (TemplateParam param : template.getAllParams()) {
        SoyValueProvider provider =
            (param.isInjected() ? ijData : data).getFieldProvider(param.name());
        if (provider == null) {
          provider =
              param.isRequired() || param.hasDefault() ? UndefinedData.INSTANCE : NullData.INSTANCE;
        }
        bind(param, provider);
      }
    }

    @Override
    void bind(VarDefn var, SoyValueProvider value) {
      localVariables.put(var, value);
    }

    @Override
    void bindLoopPosition(VarDefn loopVar, SoyValueProvider value, int index, boolean isLast) {
      LoopPosition position =
          (LoopPosition) localVariables.computeIfAbsent(loopVar, ignored -> new LoopPosition());
      position.item = value;
      position.index = index;
      position.isLast = isLast;
    }

    @Override
    SoyValueProvider getVarProvider(VarDefn var) {
      if (var.kind() == Kind.UNDECLARED) {
        // Special case for legacy templates with undeclared params.  Undeclared params aren't
        // assigned indices in the local variable table.
        SoyValueProvider provider = data.getFieldProvider(var.name());
        return provider != null ? provider : UndefinedData.INSTANCE;
      }
      Object o = localVariables.get(var);
      if (o instanceof LoopPosition) {
        return ((LoopPosition) o).item;
      }
      return (SoyValueProvider) o;
    }

    @Override
    SoyValue getVar(VarDefn var) {
      return getVarProvider(var).resolve();
    }

    @Override
    boolean isLast(VarDefn var) {
      return ((LoopPosition) localVariables.get(var)).isLast;
    }

    @Override
    int getIndex(VarDefn var) {
      return ((LoopPosition) localVariables.get(var)).index;
    }
  }

  /** An environment that is empty and returns {@link UndefinedData} for everything. */
  private static final class EmptyImpl extends Environment {
    @Override
    void bind(VarDefn var, SoyValueProvider value) {
      throw new UnsupportedOperationException();
    }

    @Override
    void bindLoopPosition(VarDefn loopVar, SoyValueProvider value, int index, boolean isLast) {
      throw new UnsupportedOperationException();
    }

    @Override
    SoyValueProvider getVarProvider(VarDefn var) {
      return UndefinedData.INSTANCE;
    }

    @Override
    SoyValue getVar(VarDefn var) {
      return UndefinedData.INSTANCE;
    }

    @Override
    boolean isLast(VarDefn loopVar) {
      return UndefinedData.INSTANCE.booleanValue();
    }

    @Override
    int getIndex(VarDefn loopVar) {
      return UndefinedData.INSTANCE.integerValue();
    }
  }
}
