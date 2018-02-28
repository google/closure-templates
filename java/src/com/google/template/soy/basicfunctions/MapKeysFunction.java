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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that gets the keys in a map.
 *
 * <p>The keys are returned as a list with no guarantees on the order (may be different on each run
 * or for each backend).
 *
 * <p>This enables iteration over the keys in a map, e.g. {@code {foreach $key in keys($myMap)} ...
 * {/foreach}}
 *
 * <p>NOTE: this function has special support in the type checker for calculating the return type
 *
 */
@Singleton
@SoyPureFunction
public final class MapKeysFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  MapKeysFunction() {}

  @Override
  public String getName() {
    return "mapKeys";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);

    if (!(arg instanceof SoyMap)) {
      throw new IllegalArgumentException("Argument to mapKeys() function is not a SoyMap.");
    }

    return ListImpl.forProviderList(BasicFunctionsRuntime.mapKeys((SoyMap) arg));
  }

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
  private static final class JbcSrcMethods {
    static final MethodRef MAP_KEYS_FN =
        MethodRef.create(BasicFunctionsRuntime.class, "mapKeys", SoyMap.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression soyExpression = args.get(0);
    SoyType argType = soyExpression.soyType();
    SoyType keyType = ((MapType) argType).getKeyType();
    return SoyExpression.forList(
        keyType == null ? ListType.EMPTY_LIST : ListType.of(keyType),
        JbcSrcMethods.MAP_KEYS_FN.invoke(soyExpression.box().checkedCast(SoyMap.class)));
  }
}
