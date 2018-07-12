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

package com.google.template.soy.basicfunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyMap;
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
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that gets the keys in a map.
 *
 * <p>The keys are returned as a list with no guarantees on the order (may be different on each run
 * or for each backend).
 *
 * <p>This enables iteration over the keys in a map, e.g. {@code {for $key in keys($myMap)} ...
 * {/for}}
 *
 */
@SoyFunctionSignature(
    name = "mapKeys",
    // Note: the return type is overridden in ResolveTypeExpressionsPass
    value = @Signature(parameterTypes = "map<?, ?>", returnType = "list<?>"))
@SoyPureFunction
public final class MapKeysFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    return new JsExpr("soy.map.$$getMapKeys(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>of("soy.map");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);

    return new PyListExpr("(" + arg.getText() + ").keys()", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method MAP_KEYS_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "mapKeys", SoyMap.class);
    static final MethodRef MAP_KEYS_FN_REF = MethodRef.create(MAP_KEYS_FN);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.MAP_KEYS_FN, args.get(0));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression soyExpression = args.get(0);
    SoyType argType = soyExpression.soyType();
    SoyType keyType = ((MapType) argType).getKeyType();
    return SoyExpression.forList(
        keyType == null ? ListType.EMPTY_LIST : ListType.of(keyType),
        Methods.MAP_KEYS_FN_REF.invoke(soyExpression.box().checkedCast(SoyMap.class)));
  }
}
