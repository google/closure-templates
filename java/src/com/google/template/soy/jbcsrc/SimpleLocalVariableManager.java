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

import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.internal.JbcSrcNameGenerators;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * A class that can manage local variable lifetimes for a method.
 *
 * <p>In bytecode local variables are assigned indices which we need to manage.
 */
final class SimpleLocalVariableManager implements LocalVariableManager {
  // Locals have the same rules as field names so we can just use the same mangling strategy.
  private final UniqueNameGenerator localNames = JbcSrcNameGenerators.forFieldNames();
  private final List<LocalVariable> allVariables = new ArrayList<>();
  private final BitSet availableSlots = new BitSet();
  private final Map<String, LocalVariable> activeVariables = new HashMap<>();
  private boolean generated;

  SimpleLocalVariableManager(Method method, boolean isStatic) {
    if (!isStatic) {
      // for 'this'
      reserveSlotFor(BytecodeUtils.OBJECT.type());
      localNames.claimName("this");
    }
    for (Type type : method.getArgumentTypes()) {
      reserveSlotFor(type);
    }
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
    final List<LocalVariable> frame = new ArrayList<>();
    return new Scope() {
      final Label scopeExit = new Label();
      boolean exited;

      @Override
      public LocalVariable createNamedLocal(String name, Type type) {
        // TODO(lukes): ideally we would use 'claimName' here but for that to work we also need an
        // 'unclaimName' api, which we don't have yet.
        LocalVariable var = createTemporary(name, type);
        activeVariables.put(name, var);
        return var;
      }

      @Override
      public LocalVariable createTemporary(String proposedName, Type type) {
        checkState(!generated);
        checkState(!exited);
        String name = localNames.generateName(proposedName);
        int slot = reserveSlotFor(type);
        LocalVariable var =
            LocalVariable.createLocal(
                name, slot, type, /* start=*/ new Label(), /* end=*/ scopeExit);
        frame.add(var);
        return var;
      }

      @Override
      public Statement exitScope() {
        checkState(!generated);
        checkState(!exited);
        exited = true;
        for (LocalVariable var : frame) {
          availableSlots.clear(var.index(), var.index() + var.resultType().getSize());
          activeVariables.remove(var.variableName());
        }
        return new Statement() {
          @Override
          protected void doGen(CodeBuilder adapter) {
            adapter.mark(scopeExit);
          }
        };
      }
    };
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
