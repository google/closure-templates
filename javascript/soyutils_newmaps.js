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

/**
 * @fileoverview Functions that create new Maps and can't live in
 * soyutils_map.js due to b/72879961.
 */
// TODO(b/72879961): these Map instantiations cause JSCompiler to be more
// conservative in dead code elimination. The result is a uniform 100-200 byte
// increase in the transitive size of every module, even if no Soy template uses
// these functions. As a workaround, put these functions into their own file so
// that they are DCE'd by AJD instead of JSCompiler.
goog.module('soy.newmaps');

const googArray = goog.require('goog.array');

/**
 * Converts a legacy object map with string keys into an equivalent Map.
 * @param {!Object<V>} obj
 * @return {!Map<string, V>}
 * @template V
 */
function $$legacyObjectMapToMap(obj) {
  const map = new Map();
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      map.set(key, obj[key]);
    }
  }
  return map;
}

/**
 * Calls a function for each value in a map and inserts the result (with the
 * same key) into a new map.
 * @param {!ReadonlyMap<K, VIn>} map The map over which to iterate.
 * @param {function((VIn|null|undefined)):VOut|function(VIn):VOut} f The
 *     function to call for every value.
 * @return {!Map<K, VOut>} a new map with the results from f
 * @template K, VIn, VOut
 */
function $$transformValues(map, f) {
  const m = new Map();
  for (const [k, v] of map.entries()) {
    m.set(k, f(v));
  }
  return m;
}


/**
 * Null-safe version of $$transformValues.
 *
 * @param {?ReadonlyMap<K, VIn>|null|undefined} map
 * @param {function((VIn|null|undefined)):VOut|function(VIn):VOut} f
 * @return {?Map<K, VOut>|null|undefined}
 * @template K, VIn, VOut
 */
function $$nullSafeTransformValues(map, f) {
  if (map == null) {
    return map;
  }
  return $$transformValues(map, f);
}


/**
 * Null-safe version of goog.array.map.
 *
 * @param {?ReadonlyArray<IN>|null|undefined} arr
 * @param {function(IN, OUT)} f
 * @return {?Array<OUT>|null|undefined}
 * @template IN, OUT
 */
function $$nullSafeArrayMap(arr, f) {
  if (arr == null) {
    return arr;
  }
  return googArray.map(arr, f);
}


exports = {
  $$legacyObjectMapToMap,
  $$transformValues,
  $$nullSafeTransformValues,
  $$nullSafeArrayMap,
};
