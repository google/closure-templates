/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.plugin.java.restricted.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.restricted.FieldRef.staticFieldReference;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.JbcSrcJavaValues;
import com.google.template.soy.jbcsrc.TestExpressionDetacher;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Allows users to test their {@link SoyJavaSourceFunction} plugins, by constructing an instanceof
 * of this class and then calling {@link #callFunction}.
 */
public class SoyJavaSourceFunctionTester {
  /** A builder for {@link SoyJavaSourceFunctionTester}. */
  public static class Builder {
    private final SoyJavaSourceFunction fn;
    @Nullable private BidiGlobalDir bidiGlobalDir;
    private ULocale locale;

    public Builder(SoyJavaSourceFunction fn) {
      this.fn = checkNotNull(fn);
    }

    @CanIgnoreReturnValue
    public Builder withBidiGlobalDir(BidiGlobalDir bidiGlobalDir) {
      this.bidiGlobalDir = checkNotNull(bidiGlobalDir);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withLocale(ULocale locale) {
      this.locale = locale;
      return this;
    }

    public SoyJavaSourceFunctionTester build() {
      return new SoyJavaSourceFunctionTester(this);
    }
  }

  private final SoyJavaSourceFunction fn;
  @Nullable private final BidiGlobalDir bidiGlobalDir;
  private final ULocale locale;

  public SoyJavaSourceFunctionTester(SoyJavaSourceFunction fn) {
    this.bidiGlobalDir = null;
    this.fn = checkNotNull(fn);
    this.locale = null;
  }

  private SoyJavaSourceFunctionTester(Builder builder) {
    this.bidiGlobalDir = builder.bidiGlobalDir;
    this.fn = builder.fn;
    this.locale = builder.locale;
  }

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} on the function, returning the resolved
   * result.
   */
  public Object callFunction(Object... args) {
    SoyFunctionSignature fnSig = fn.getClass().getAnnotation(SoyFunctionSignature.class);
    FunctionNode fnNode =
        FunctionNode.newPositional(
            Identifier.create(fnSig.name(), SourceLocation.UNKNOWN), fn, SourceLocation.UNKNOWN);
    Signature matchingSig = findMatchingSignature(fnSig.value(), args.length);
    // Setting the allowed param types requires the node have that # of children,
    // so we add fake children.
    for (int i = 0; i < matchingSig.parameterTypes().length; i++) {
      fnNode.addChild(new NullNode(SourceLocation.UNKNOWN));
    }
    fnNode.setAllowedParamTypes(
        Stream.of(matchingSig.parameterTypes()).map(this::parseType).collect(toImmutableList()));
    fnNode.setType(parseType(matchingSig.returnType()));

    try {
      return ExpressionEvaluator.evaluate(
          JbcSrcJavaValues.computeForJavaSource(
              fnNode,
              new InternalContext(),
              this::getFunctionRuntime,
              stream(args).map(this::transform).collect(toImmutableList()),
              new TestExpressionDetacher()));
    } catch (ReflectiveOperationException roe) {
      throw new RuntimeException(roe);
    }
  }

  /** See {@link #callFunction(Object...)}. */
  public final Object callFunction(Iterable<Object> args) {
    return callFunction(Iterables.toArray(args, Object.class));
  }

  public Object callMethod(Object base, Object... args) {
    SoyMethodSignature methodSig = fn.getClass().getAnnotation(SoyMethodSignature.class);
    Signature matchingSig = findMatchingSignature(methodSig.value(), args.length);

    SoyType returnType = parseType(matchingSig.returnType());

    MethodCallNode methodCallNode =
        MethodCallNode.newWithPositionalArgs(
            new NullNode(SourceLocation.UNKNOWN),
            ImmutableList.of(),
            Identifier.create(methodSig.name(), SourceLocation.UNKNOWN),
            SourceLocation.UNKNOWN,
            /* isNullSafe= */ false);
    methodCallNode.setSoyMethod(
        new SoySourceFunctionMethod(
            fn,
            parseType(methodSig.baseType()),
            returnType,
            stream(matchingSig.parameterTypes()).map(this::parseType).collect(toImmutableList()),
            methodSig.name()));
    methodCallNode.setType(returnType);

    try {
      return ExpressionEvaluator.evaluate(
          JbcSrcJavaValues.computeForJavaSource(
              methodCallNode,
              new InternalContext(),
              this::getFunctionRuntime,
              Stream.concat(Stream.of(base), stream(args))
                  .map(this::transform)
                  .collect(toImmutableList()),
              new TestExpressionDetacher()));
    } catch (ReflectiveOperationException roe) {
      throw new RuntimeException(roe);
    }
  }

  private Signature findMatchingSignature(Signature[] sigs, int numArgs) {
    for (Signature sig : sigs) {
      if (sig.parameterTypes().length == numArgs) {
        return sig;
      }
    }
    throw new IllegalArgumentException(
        "No signature on " + fn.getClass().getName() + " with " + numArgs + " parameters");
  }

  private SoyType parseType(String type) {
    TypeNode parsed =
        SoyFileParser.parseType(
            type, SourceFilePath.forTest(fn.getClass().getName()), ErrorReporter.exploding());
    return TypeNodeConverter.builder(ErrorReporter.exploding())
        .setTypeRegistry(SoyTypeRegistryBuilder.create())
        .build()
        .getOrCreateType(parsed);
  }

  /**
   * Transforms an object (runtime representation) to a SoyExpression (compile time representation).
   * This never happens during normal compilation. (ExpressionCompiler outputs SoyExpressions, but
   * its input is a Soy AST.) For unit tests we pretend that runtime objects exist as compile time
   * representations.
   */
  private SoyExpression transform(Object value) {
    if (value == null || value instanceof UndefinedData || value instanceof NullData) {
      return SoyExpression.forSoyValue(
          NullType.getInstance(), BytecodeUtils.constantNull(BytecodeUtils.NULL_DATA_TYPE));
    } else if (value instanceof Integer) {
      return SoyExpression.forInt(BytecodeUtils.constant(((Integer) value).longValue()));
    } else if (value instanceof Long) {
      return SoyExpression.forInt(BytecodeUtils.constant(((Long) value).longValue()));
    } else if (value instanceof IntegerData) {
      return SoyExpression.forInt(BytecodeUtils.constant(((IntegerData) value).longValue())).box();
    } else if (value instanceof Double) {
      return SoyExpression.forFloat(BytecodeUtils.constant(((Double) value).doubleValue()));
    } else if (value instanceof FloatData) {
      return SoyExpression.forFloat(BytecodeUtils.constant(((FloatData) value).numberValue()))
          .box();
    } else if (value instanceof String) {
      return SoyExpression.forString(BytecodeUtils.constant(((String) value)));
    } else if (value instanceof StringData) {
      return SoyExpression.forString(BytecodeUtils.constant(((StringData) value).toString())).box();
    } else if (value instanceof Boolean) {
      return SoyExpression.forBool(BytecodeUtils.constant(((Boolean) value)));
    } else if (value instanceof BooleanData) {
      return SoyExpression.forBool(BytecodeUtils.constant(((BooleanData) value).booleanValue()))
          .box();
    } else if (value instanceof SanitizedContent) {
      SanitizedContent content = (SanitizedContent) value;
      Expression sanitizedExpr =
          MethodRefs.ORDAIN_AS_SAFE_DIR.invoke(
              BytecodeUtils.constant(content.toString()),
              BytecodeUtils.constant(content.getContentKind()),
              BytecodeUtils.constant(content.getContentDirection()));
      SoyType type =
          SanitizedType.getTypeForContentKind(
              Converters.toSanitizedContentKind(content.getContentKind()));
      return SoyExpression.forSoyValue(type, sanitizedExpr);
    } else if (value instanceof SoyDict) {
      List<Expression> keys = new ArrayList<>();
      List<Expression> values = new ArrayList<>();
      SoyDict dict = (SoyDict) value;
      for (Map.Entry<String, ? extends SoyValue> entry :
          dict.asResolvedJavaStringMap().entrySet()) {
        keys.add(transform(entry.getKey()));
        values.add(transform(entry.getValue()));
      }
      return SoyExpression.forSoyValue(
          UnknownType.getInstance(),
          MethodRefs.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
              BytecodeUtils.newLinkedHashMap(keys, values),
              FieldRef.enumReference(RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
                  .accessor()));
    } else if (value instanceof SoyMap) {
      List<Expression> keys = new ArrayList<>();
      List<Expression> values = new ArrayList<>();
      SoyMap map = (SoyMap) value;
      for (Map.Entry<? extends SoyValue, ? extends SoyValueProvider> entry :
          map.asJavaMap().entrySet()) {
        keys.add(transform(entry.getKey()));
        values.add(transform(entry.getValue().resolve()));
      }
      return SoyExpression.forSoyValue(
          UnknownType.getInstance(),
          MethodRefs.MAP_IMPL_FOR_PROVIDER_MAP.invoke(
              BytecodeUtils.newLinkedHashMap(keys, values)));
    } else if (value instanceof SoyList) {
      List<Expression> items = new ArrayList<>();
      for (SoyValue item : ((SoyList) value).asResolvedJavaList()) {
        items.add(transform(item));
      }
      return SoyExpression.forList(
          ListType.of(UnknownType.getInstance()), BytecodeUtils.asList(items));
    }
    throw new UnsupportedOperationException(
        "Values of type: " + value.getClass() + " not supported yet");
  }

  private Expression getFunctionRuntime(String fnName) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private static final MethodRef ULOCALE =
      MethodRef.createPureConstructor(ULocale.class, String.class);

  private class InternalContext implements JbcSrcPluginContext {

    @Override
    public Expression getULocale() {
      return ULOCALE.invoke(BytecodeUtils.constant(locale.toString()));
    }

    @Override
    public Expression getAllRequiredCssNamespaces(SoyExpression template) {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Expression getAllRequiredCssPaths(SoyExpression template) {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Expression getBidiGlobalDir() {
      if (bidiGlobalDir == BidiGlobalDir.RTL) {
        return staticFieldReference(BidiGlobalDir.class, "RTL").accessor();
      }
      if (bidiGlobalDir == BidiGlobalDir.LTR) {
        return staticFieldReference(BidiGlobalDir.class, "LTR").accessor();
      }
      throw new IllegalStateException("no bidiGlobalDir set.");
    }
  }
}
