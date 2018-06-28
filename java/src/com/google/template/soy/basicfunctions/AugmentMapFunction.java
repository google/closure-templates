/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that creates a new map equivalent to augmenting an existing map with additional
 * mappings.
 *
 */
@SoyFunctionSignature(
    name = "augmentMap",
    value =
        // TODO(b/70946095): should be map<?, ?>, but due to the map migration we are leaving it as
        // unknown for now.
        @Signature(
            returnType = "?",
            parameterTypes = {"?", "?"}))
@SoyPureFunction
public final class AugmentMapFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    JsExpr arg1 = args.get(1);

    String exprText = "soy.$$augmentMap(" + arg0.getText() + ", " + arg1.getText() + ")";
    return new JsExpr(exprText, Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class MethodRefs {
    static final Method AUGMENT_MAP_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "augmentMap", SoyDict.class, SoyDict.class);

    static final MethodRef AUGMENT_MAP_FN_REF = MethodRef.create(AUGMENT_MAP_FN).asNonNullable();
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    JavaValue arg0 = args.get(0);
    JavaValue arg1 = args.get(1);

    // TODO(sameb): Update ResolveExpressionTypesPass to set the node type, so it's set correctly.
    return factory.callStaticMethod(MethodRefs.AUGMENT_MAP_FN, arg0, arg1);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression arg0 = args.get(0);
    SoyExpression arg1 = args.get(1);
    Expression first = arg0.checkedCast(SoyDict.class);
    Expression second = arg1.checkedCast(SoyDict.class);
    // TODO(lukes): this logic should move into the ResolveExpressionTypesPass
    LegacyObjectMapType mapType =
        LegacyObjectMapType.of(
            StringType.getInstance(),
            UnionType.of(getMapValueType(arg0.soyType()), getMapValueType(arg1.soyType())));
    return SoyExpression.forSoyValue(mapType, MethodRefs.AUGMENT_MAP_FN_REF.invoke(first, second));
  }

  private SoyType getMapValueType(SoyType type) {
    if (type.getKind() == Kind.LEGACY_OBJECT_MAP) {
      return ((LegacyObjectMapType) type).getValueType();
    }
    return UnknownType.getInstance();
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyFunctionExprBuilder fnBuilder = new PyFunctionExprBuilder("dict");
    fnBuilder.addArg(args.get(0)).setUnpackedKwargs(args.get(1));
    return fnBuilder.asPyExpr();
  }
}
