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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsanitizedString;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.JbcSrcJavaValues;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
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

    public Builder(SoyJavaSourceFunction fn) {
      this.fn = checkNotNull(fn);
    }

    public Builder withBidiGlobalDir(BidiGlobalDir bidiGlobalDir) {
      this.bidiGlobalDir = checkNotNull(bidiGlobalDir);
      return this;
    }

    public SoyJavaSourceFunctionTester build() {
      return new SoyJavaSourceFunctionTester(this);
    }
  }

  private final SoyJavaSourceFunction fn;
  @Nullable private final BidiGlobalDir bidiGlobalDir;

  public SoyJavaSourceFunctionTester(SoyJavaSourceFunction fn) {
    this.bidiGlobalDir = null;
    this.fn = checkNotNull(fn);
  }

  private SoyJavaSourceFunctionTester(Builder builder) {
    this.bidiGlobalDir = builder.bidiGlobalDir;
    this.fn = builder.fn;
  }

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} (with a null context) on the function,
   * returning the resolved result.
   */
  public Object callFunction(Object... args) {
    SoyFunctionSignature fnSig = fn.getClass().getAnnotation(SoyFunctionSignature.class);
    FunctionNode fnNode =
        new FunctionNode(
            Identifier.create(fnSig.name(), SourceLocation.UNKNOWN), fn, SourceLocation.UNKNOWN);
    Signature matchingSig = null;
    for (Signature sig : fnSig.value()) {
      if (sig.parameterTypes().length == args.length) {
        matchingSig = sig;
        break;
      }
    }
    if (matchingSig == null) {
      throw new IllegalArgumentException(
          "No signature on " + fn.getClass().getName() + " with " + args.length + " parameters");
    }
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
              Stream.of(args).map(this::transform).collect(toImmutableList())));
    } catch (ReflectiveOperationException roe) {
      throw new RuntimeException(roe);
    }
  }

  private SoyType parseType(String type) {
    TypeNode parsed =
        SoyFileParser.parseType(type, fn.getClass().getName(), ErrorReporter.exploding());
    return new TypeNodeConverter(ErrorReporter.exploding(), new SoyTypeRegistry())
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
      return SoyExpression.NULL;
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
          MethodRef.ORDAIN_AS_SAFE_DIR.invoke(
              BytecodeUtils.constant(content.toString()),
              BytecodeUtils.constant(content.getContentKind()),
              BytecodeUtils.constant(content.getContentDirection()));
      SoyType type =
          SanitizedType.getTypeForContentKind(
              SanitizedContentKind.valueOf(content.getContentKind().name()));
      // If the content is TEXT, we have to cast the expression to UnsanitizedString,
      // otherwise forSoyValue fails because 'type' is StringType (which expects a SoyString),
      // whereas the expression is a SanitizedContent (which doesn't implement SoyString).
      // The expression is actually a UnsanitizedString, which implements both
      // SoyString & SanitizedContent.
      if (content.getContentKind() == ContentKind.TEXT) {
        sanitizedExpr = sanitizedExpr.checkedCast(UnsanitizedString.class);
      }
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
          MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
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
          MethodRef.MAP_IMPL_FOR_PROVIDER_MAP.invoke(BytecodeUtils.newLinkedHashMap(keys, values)));
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

  private class InternalContext implements JbcSrcPluginContext {
    @Override
    public Expression getULocale() {
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
