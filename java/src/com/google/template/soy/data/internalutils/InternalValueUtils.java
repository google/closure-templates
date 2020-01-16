/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.data.internalutils;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.StringNode;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Internal utilities related to Soy values.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class InternalValueUtils {

  private InternalValueUtils() {}

  /**
   * Converts a primitive data object into a primitive expression node.
   *
   * @param primitiveData The primitive data object to convert. Must not be undefined.
   * @param location The node's source location.
   * @return The resulting primitive expression node.
   */
  @Nullable
  public static PrimitiveNode convertPrimitiveDataToExpr(
      PrimitiveData primitiveData, SourceLocation location) {
    if (primitiveData instanceof StringData) {
      return new StringNode(primitiveData.stringValue(), QuoteStyle.SINGLE, location);
    } else if (primitiveData instanceof BooleanData) {
      return new BooleanNode(primitiveData.booleanValue(), location);
    } else if (primitiveData instanceof IntegerData) {
      // NOTE: We only support numbers in the range of JS [MIN_SAFE_INTEGER, MAX_SAFE_INTEGER]
      if (!IntegerNode.isInRange(primitiveData.longValue())) {
        return null;
      } else {
        return new IntegerNode(primitiveData.longValue(), location);
      }
    } else if (primitiveData instanceof FloatData) {
      return new FloatNode(primitiveData.floatValue(), location);
    } else if (primitiveData instanceof NullData) {
      return new NullNode(location);
    } else {
      // Annoyingly UndefinedData.toString() throws, so workaround.
      throw new IllegalArgumentException(
          "can't convert: "
              + (primitiveData instanceof UndefinedData ? "undefined" : primitiveData)
              + " to an ExprNode");
    }
  }

  /**
   * Converts a primitive expression node into a primitive data object.
   *
   * @param primitiveNode The primitive expression node to convert.
   * @return The resulting primitive data object.
   */
  public static PrimitiveData convertPrimitiveExprToData(PrimitiveNode primitiveNode) {

    if (primitiveNode instanceof StringNode) {
      return StringData.forValue(((StringNode) primitiveNode).getValue());
    } else if (primitiveNode instanceof BooleanNode) {
      return BooleanData.forValue(((BooleanNode) primitiveNode).getValue());
    } else if (primitiveNode instanceof IntegerNode) {
      return IntegerData.forValue(((IntegerNode) primitiveNode).getValue());
    } else if (primitiveNode instanceof FloatNode) {
      return FloatData.forValue(((FloatNode) primitiveNode).getValue());
    } else if (primitiveNode instanceof NullNode) {
      return NullData.INSTANCE;
    } else {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Converts a compile-time globals map in user-provided format into one in the internal format.
   *
   * <p>The returned map will have the same iteration order as the provided map.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be any
   *     of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @return An equivalent map in the internal format.
   * @throws IllegalArgumentException If the map contains an invalid value.
   */
  public static ImmutableMap<String, PrimitiveData> convertCompileTimeGlobalsMap(
      Map<String, ?> compileTimeGlobalsMap) {

    ImmutableMap.Builder<String, PrimitiveData> resultMapBuilder = ImmutableMap.builder();

    for (Map.Entry<String, ?> entry : compileTimeGlobalsMap.entrySet()) {

      Object valueObj = entry.getValue();
      PrimitiveData value;
      boolean isValidValue = true;
      try {
        SoyValue value0 = SoyValueConverter.INSTANCE.convert(valueObj).resolve();
        if (!(value0 instanceof PrimitiveData)) {
          isValidValue = false;
        }
        value = (PrimitiveData) value0;
      } catch (SoyDataException sde) {
        isValidValue = false;
        value = null; // make compiler happy
      }
      if (!isValidValue) {
        throw new IllegalArgumentException(
            "Compile-time globals map contains invalid value: "
                + valueObj
                + " for key: "
                + entry.getKey());
      }

      resultMapBuilder.put(entry.getKey(), value);
    }

    return resultMapBuilder.build();
  }
}
