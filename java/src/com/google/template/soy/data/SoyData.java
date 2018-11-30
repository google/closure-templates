/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Abstract base class for all nodes in a Soy data tree.
 *
 * <p>Important: Even though this class is not marked 'final', do not extend this class.
 *
 */
public abstract class SoyData extends SoyAbstractValue {

  /**
   * Creates deprecated SoyData objects from standard Java data structures.
   *
   * @param obj The existing object or data structure to convert.
   * @return A SoyData object or tree that corresponds to the given object.
   * @throws SoyDataException If the given object cannot be converted to SoyData.
   * @deprecated It's best to pass whatever object you have directly to the Soy templates you're
   *     using -- Soy understands primitives, lists, and maps natively, and if you install runtime
   *     support you can also pass protocol buffers. If you're interacting directly with the Soy
   *     runtime and need SoyValue objects, use SoyValueConverter instead.
   */
  @Deprecated
  protected static SoyData createFromExistingData(Object obj) {
    if (obj instanceof SoyData) {
      return (SoyData) obj;
    } else if (obj instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      Map<String, ?> objCast = (Map<String, ?>) obj;
      return new SoyMapData(objCast);
    } else if (obj instanceof Iterable<?>) {
      return new SoyListData((Iterable<?>) obj);
    } else if (obj instanceof Future<?>) {
      // Note: In the old SoyData, we don't support late-resolution of Futures. We immediately
      // resolve the Future object here. For late-resolution, use SoyValueConverter.convert().
      try {
        return createFromExistingData(((Future<?>) obj).get());
      } catch (InterruptedException e) {
        throw new SoyDataException(
            "Encountered InterruptedException when resolving Future object.", e);
      } catch (ExecutionException e) {
        throw new SoyDataException(
            "Encountered ExecutionException when resolving Future object.", e);
      }
    } else {
      SoyValue soyValue = SoyValueConverter.INSTANCE.convert(obj).resolve();
      if (soyValue instanceof SoyData) {
        return (SoyData) soyValue;
      }
      throw new SoyDataException(
          "Attempting to convert unrecognized object to Soy data (object type "
              + obj.getClass().getName()
              + ").");
    }
  }
}
