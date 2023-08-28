/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.internal.JbcSrcNameGenerators;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * A class that can manage local variable lifetimes for a method.
 *
 * <p>In bytecode local variables are assigned indices which we need to manage.
 */
final class SimpleLocalVariableManager implements LocalVariableManager {
  // Locals have the same rules as field names so we can just use the same mangling strategy.
  private final ArrayDeque<UniqueNameGenerator> localNames = new ArrayDeque<>();
  private final List<LocalVariable> allVariables = new ArrayList<>();
  private final BitSet availableSlots = new BitSet();
  private final Map<String, LocalVariable> activeVariables = new LinkedHashMap<>();
  private boolean generated;
  private final boolean isStatic;
  private Type[] parameterTypes;
  private final Label methodBegin;
  private final Label methodEnd;

  SimpleLocalVariableManager(Type ownerType, boolean isStatic) {
    this(ownerType, new Type[0], ImmutableList.of(), null, null, isStatic);
  }

  SimpleLocalVariableManager(
      Type ownerType,
      Type[] argumentTypes,
      List<String> parameterNames,
      Label methodBegin,
      Label methodEnd,
      boolean isStatic) {
    checkArgument(
        argumentTypes.length == parameterNames.size(),
        "expected %s args, got %s paramNames: %s",
        argumentTypes.length,
        parameterNames.size(),
        parameterNames);
    this.localNames.addLast(JbcSrcNameGenerators.forFieldNames());
    this.isStatic = isStatic;
    this.parameterTypes = argumentTypes;
    this.methodBegin = methodBegin;
    this.methodEnd = methodEnd;
    if (!isStatic) {
      reserveParameter("this", ownerType);
    }
    int parameterIndex = 0;
    for (Type type : argumentTypes) {
      reserveParameter(parameterNames.get(parameterIndex), type);
      parameterIndex++;
    }
  }

  public void updateParameterTypes(Type[] parameterTypes, List<String> parameterNames) {
    Set<String> allAllocatedVariableNames =
        allVariables.stream().map(v -> v.variableName()).collect(toCollection(HashSet::new));
    // We could support more changes (e.g. appending parameters, support instance methods), but
    // it doesn't appear to be needed and this is simpler.
    checkArgument(
        this.parameterTypes.length == 0 && isStatic,
        "Can only change parameters if the variable manager original had no parameters and was"
            + " static.");
    this.parameterTypes = parameterTypes;
    int spaceForParameters = 0;
    for (Type type : parameterTypes) {
      spaceForParameters += type.getSize();
    }
    // We need to shift all allocated variables to account for the new parameters.
    for (int i = availableSlots.size() - 1; (i = availableSlots.previousSetBit(i)) >= 0; ) {
      availableSlots.set(i + spaceForParameters);
      availableSlots.clear(i);
    }
    for (var var : allVariables) {
      var.shiftIndex(spaceForParameters);
    }
    // now allocate the parameters
    int parameterIndex = 0;
    for (Type type : parameterTypes) {
      String name = parameterNames.get(parameterIndex);
      // because we are adding names late we need to mangle
      while (!allAllocatedVariableNames.add(name)) {
        name = "$" + name;
      }
      reserveParameter(name, type);
      parameterIndex++;
    }
  }

  private void reserveParameter(String name, Type type) {
    int slot = reserveSlotFor(type);
    localNames.peek().exact(name);
    LocalVariable var =
        LocalVariable.createLocal(name, slot, type, /* start=*/ methodBegin, /* end=*/ methodEnd);
    allVariables.add(var);
    activeVariables.put(name, var);
  }

  @Override
  public Expression getVariable(String name) {
    LocalVariable var = activeVariables.get(name);
    if (var == null) {
      throw new IllegalArgumentException(
          "Can't find variable: "
              + name
              + " among the active variables: "
              + activeVariables.keySet());
    }
    return var;
  }

  ImmutableMap<String, LocalVariable> allActiveVariables() {
    return ImmutableMap.copyOf(activeVariables);
  }

  @Override
  public void generateTableEntries(CodeBuilder cb) {
    generated = true;
    for (LocalVariable var : allVariables) {
      try {
        var.tableEntry(cb);
      } catch (Throwable t) {
        throw new RuntimeException("unable to write table entry for: " + var, t);
      }
    }
  }

  @Override
  public Scope enterScope() {
    checkState(!generated);
    List<LocalVariable> frame = new ArrayList<>();
    UniqueNameGenerator scopeNames = localNames.peekLast().branch();
    localNames.addLast(scopeNames);
    return new Scope() {
      final Label scopeExit = new Label();
      boolean exited;

      @Override
      public LocalVariable createNamedLocal(String name, Type type) {
        LocalVariable var = createTemporary(name, type);
        activeVariables.put(name, var);
        return var;
      }

      @Override
      public LocalVariable createTemporary(String proposedName, Type type) {
        checkState(!generated);
        checkState(!exited);
        String name = scopeNames.generate(proposedName);
        int slot = reserveSlotFor(type);
        LocalVariable var =
            LocalVariable.createLocal(
                name, slot, type, /* start= */ new Label(), /* end= */ scopeExit);
        allVariables.add(var);
        frame.add(var);
        return var;
      }

      @Override
      public Statement exitScope() {
        checkState(!generated);
        checkState(!exited);
        exited = true;
        for (LocalVariable var : frame) {
          returnSlotFor(var);
          activeVariables.remove(var.variableName());
        }
        localNames.removeLast();
        return Statement.NULL_STATEMENT.labelStart(scopeExit);
      }
    };
  }

  private void returnSlotFor(LocalVariable var) {
    availableSlots.clear(var.index(), var.index() + var.resultType().getSize());
  }

  private int reserveSlotFor(Type type) {
    int size = type.getSize();
    checkArgument(size == 1 || size == 2); // void has size 0
    int start = 0;
    // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3
    // this is the maximum number of local variables
    while (start < 65536) {
      int nextClear = availableSlots.nextClearBit(start);
      if (size == 1 || (size == 2 && !availableSlots.get(nextClear + 1))) {
        availableSlots.set(nextClear, nextClear + size);
        return nextClear;
      }
      // keep looking
      start = nextClear + 1;
    }
    throw new RuntimeException("too many local variables");
  }
}
