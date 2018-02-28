/*
 * Copyright 2017 Google Inc.
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
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Converts values of type {@code map} to values of type {@code legacy_object_map}.
 *
 * <p>(This is the inverse of {@link LegacyObjectMapToMapFunction}.)
 *
 * <p>The two map types are designed to be incompatible in the Soy type system; the long-term plan
 * is to migrate all {@code legacy_object_map}s to {@code map}s and delete {@code
 * legacy_object_map}. To allow template-level migrations of {@code legacy_object_map} parameters to
 * {@code map}, we need plugins to convert between the two maps, so that converting one template
 * doesn't require converting its transitive callees.
 *
 * <p>NOTE: this function has special support in the type checker for calculating the return type
 */
public final class MapToLegacyObjectMapFunction
    implements SoyJavaFunction,
        SoyJbcSrcFunction,
        SoyPySrcFunction,
        SoyLibraryAssistedJsSrcFunction {

  @Inject
  MapToLegacyObjectMapFunction() {}

  @Override
  public String getName() {
    return "mapToLegacyObjectMap";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy.map");
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef MAP_TO_LEGACY_OBJECT_MAP =
        MethodRef.create(BasicFunctionsRuntime.class, "mapToLegacyObjectMap", SoyMapImpl.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression soyExpression = Iterables.getOnlyElement(args);
    SoyType originalType = soyExpression.soyRuntimeType().soyType();
    LegacyObjectMapType newType;
    if (originalType instanceof MapType) {
      newType =
          LegacyObjectMapType.of(
              ((MapType) originalType).getKeyType(), ((MapType) originalType).getValueType());
    } else if (originalType instanceof UnknownType) {
      newType = LegacyObjectMapType.of(UnknownType.getInstance(), UnknownType.getInstance());
    } else {
      throw new IllegalArgumentException(
          "mapToLegacyObjectMap() expects input to be MAP, get " + originalType.getKind());
    }
    return SoyExpression.forLegacyObjectMap(
        newType,
        JbcSrcMethods.MAP_TO_LEGACY_OBJECT_MAP.invoke(
            soyExpression.box().checkedCast(SoyMapImpl.class)));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    return new JsExpr(
        "soy.map.$$mapToLegacyObjectMap(" + args.get(0).getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    String map = Iterables.getOnlyElement(args).getText();
    return new PyExpr(
        String.format("runtime.map_to_legacy_object_map(%s)", map), Integer.MAX_VALUE);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyMapImpl map = (SoyMapImpl) Iterables.getOnlyElement(args);
    return BasicFunctionsRuntime.mapToLegacyObjectMap(map);
  }
}
