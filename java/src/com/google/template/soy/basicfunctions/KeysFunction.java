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
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.UnknownType;
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
 */
@Singleton
@SoyPureFunction
public final class KeysFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  KeysFunction() {}

  @Override
  public String getName() {
    return "keys";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);

    if (!(arg instanceof SoyMap)) {
      throw new IllegalArgumentException("Argument to keys() function is not SoyMap.");
    }

    return ListImpl.forProviderList(BasicFunctionsRuntime.keys((SoyMap) arg));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    return new JsExpr("soy.$$getMapKeys(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);

    return new PyListExpr("(" + arg.getText() + ").keys()", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef KEYS_FN =
        MethodRef.create(BasicFunctionsRuntime.class, "keys", SoyMap.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(Context context, List<SoyExpression> args) {
    SoyExpression soyExpression = args.get(0);
    SoyType argType = soyExpression.soyType();
    // TODO(lukes): this logic should live in ResolveExpressionTypesVisitor
    SoyType listElementType;
    if (argType.getKind() == SoyType.Kind.MAP) {
      listElementType = ((MapType) argType).getKeyType(); // pretty much just string
    } else if (argType.getKind() == SoyType.Kind.LIST) {
      listElementType = IntType.getInstance();
    } else {
      listElementType = UnknownType.getInstance();
    }
    return SoyExpression.forList(
        ListType.of(listElementType),
        JbcSrcMethods.KEYS_FN.invoke(soyExpression.box().checkedCast(SoyMap.class)));
  }
}
