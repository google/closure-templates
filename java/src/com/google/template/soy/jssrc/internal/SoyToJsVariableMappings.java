/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Manages the mappings between Soy variables and their JavaScript equivalents inside a single
 * template.
 */
public final class SoyToJsVariableMappings {
  /**
   * A VarKey is a logical reference to a local variable. This allows us to abstract the identity of
   * a local variable from the particular name that is selected for it.
   *
   * <p>This object should be treated as an opaque token.
   */
  @AutoValue
  public abstract static class VarKey {
    enum Type {
      SYNTHETIC,
      USER_DEFINED
    }

    /**
     * A synthetic variable is a variable that is needed by the code generation but does not
     * correspond to a user defined symbol.
     *
     * <p>Many synthetic variables that are referneced multiple times have dedicated constructors
     * below.
     */
    public static VarKey createSyntheticVariable(String desiredName, SoyNode declaringNode) {
      return create(desiredName, declaringNode, Type.SYNTHETIC);
    }

    /** Creates the standard top level output variable. */
    public static VarKey createOutputVar(TemplateNode declaringNode) {
      return createSyntheticVariable("output", declaringNode);
    }

    /** Creates a temporary output variable needed for the given node. */
    public static VarKey createOutputVar(CallParamContentNode declaringNode) {
      return createSyntheticVariable("param_" + declaringNode.getKey().identifier(), declaringNode);
    }
    /** Creates a temporary output variable needed for the given node. */
    static VarKey createOutputVar(LogNode declaringNode) {
      return createSyntheticVariable("log", declaringNode);
    }
    /** Creates a temporary output variable needed for the given node. */
    static VarKey createOutputVar(MsgHtmlTagNode node) {
      return createSyntheticVariable("htmlTag", node);
    }

    /** Creates a key for a local variable. */
    public static VarKey createLocalVar(LocalVarNode localVar) {
      return create(localVar.getVarName(), localVar, Type.USER_DEFINED);
    }

    /** Creates a key for a template parameter. */
    public static VarKey createParam(TemplateParam param) {
      // Params don't have an easily accessible 'declaringNode'
      // however we can use the TemplateParam object itself as part of the key
      return new AutoValue_SoyToJsVariableMappings_VarKey(
          param.name(), null, param, Type.USER_DEFINED);
    }

    /** Creates a key for referencing the {@code for} node {@code range} limit. */
    static VarKey createLimitVar(ForNode node) {
      return createSyntheticVariable(node.getVarName() + "Limit", node);
    }

    /** Creates a key for referencing the {@code for} node {@code range} increment. */
    static VarKey createIncrementVar(ForNode node) {
      return createSyntheticVariable(node.getVarName() + "Increment", node);
    }

    /** Creates a key for referencing the {@code foreach} node list expression. */
    static VarKey createListVar(ForeachNonemptyNode loopNode) {
      return createSyntheticVariable(loopNode.getVarName() + "List", loopNode);
    }

    /** Creates a key for referencing the length of the foreach list expression. */
    static VarKey createListLenVar(ForeachNonemptyNode loopNode) {
      return createSyntheticVariable(loopNode.getVarName() + "ListLen", loopNode);
    }

    /** Creates a key for referencing current index of the foreach loop. */
    static VarKey createIndexVar(ForeachNonemptyNode loopNode) {
      return createSyntheticVariable(loopNode.getVarName() + "Index", loopNode);
    }

    private static VarKey create(String desiredName, SoyNode owner, Type type) {
      return new AutoValue_SoyToJsVariableMappings_VarKey(
          desiredName, checkNotNull(owner), null, type);
    }

    abstract String desiredName();

    @Nullable
    abstract SoyNode declaringNode();

    @Nullable
    abstract TemplateParam param();

    abstract Type type();
  }

  private final Map<VarKey, CodeChunk.WithValue> mappings;
  private final UniqueNameGenerator nameGenerator;

  private SoyToJsVariableMappings(UniqueNameGenerator nameGenerator) {
    this.mappings = new HashMap<>();
    this.nameGenerator = checkNotNull(nameGenerator);
  }

  /** Returns a new {@link SoyToJsVariableMappings} suitable for translating an entire template. */
  public static SoyToJsVariableMappings create(UniqueNameGenerator nameGenerator) {
    return new SoyToJsVariableMappings(nameGenerator);
  }

  /** Returns a {@link SoyToJsVariableMappings} seeded with the given mappings. For testing only. */
  @VisibleForTesting
  static SoyToJsVariableMappings startingWith(
      ImmutableMap<VarKey, CodeChunk.WithValue> initialMappings,
      UniqueNameGenerator nameGenerator) {
    SoyToJsVariableMappings soyToJsVariableMappings = new SoyToJsVariableMappings(nameGenerator);
    soyToJsVariableMappings.mappings.putAll(initialMappings);
    return soyToJsVariableMappings;
  }

  /** Maps the Soy variable named identified by {@code VarKey} to the given translation. */
  public String createName(VarKey var) {
    String actualName = nameGenerator.generateName(var.desiredName());
    registerMapping(var, CodeChunk.id(actualName));
    return actualName;
  }

  /**
   * Registers a variable to be backed by a preexisting code chunk.
   *
   * <p>This may be useful for creating a {@link VarKey} that acts as an alias to another {@code
   * VarKey}.
   */
  public void registerMapping(VarKey var, CodeChunk.WithValue value) {
    CodeChunk.WithValue prev = mappings.put(var, value);
    if (prev != null) {
      throw new IllegalStateException(var + " already has a name: " + prev);
    }
  }

  /** Returns the JavaScript translation for the Soy variable with the given name, */
  public CodeChunk.WithValue getIdentifier(VarKey key) {
    CodeChunk.WithValue reference = mappings.get(key);
    Preconditions.checkState(reference != null, "no mapping for %s exists", key);
    return reference;
  }

  /**
   * Returns the JavaScript translation for the Soy variable with the given name, or null if no
   * mapping exists for that variable. TODO(brndn): the null case is only for handling template
   * params in test scenarios. Eliminate the @Nullable by seeding {@link #create} with the params.
   */
  @Nullable
  public CodeChunk.WithValue maybeGetIdentifier(VarKey name) {
    return mappings.get(name);
  }

  /**
   * Look up a variable based on the user specified name.
   *
   * <p>This is only used by {@link V1JsExprTranslator} and is necessary for backwards
   * compatibility.
   */
  @Nullable
  CodeChunk.WithValue maybeGetIdentifierForV1ExpressionReference(String reference) {
    for (Map.Entry<VarKey, CodeChunk.WithValue> entry : mappings.entrySet()) {
      VarKey key = entry.getKey();
      // If the var has the same desired name and the name corresponds to a local or param name
      // then we can use it
      // this ensures we don't match 'derived' names, like the length of a foreach var list.
      if (reference.equals(key.desiredName())
          && ((key.declaringNode() instanceof LocalVarNode
                  && ((LocalVarNode) key.declaringNode()).getVarName().equals(reference))
              || (key.param() != null && key.param().name().equals(reference)))) {
        return entry.getValue();
      }
    }
    return null;
  }
}
