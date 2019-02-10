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
goog.module.declareLegacyNamespace();

const {Map: SoyMap} = goog.require('soy.map');

/**
 * Converts a legacy object map with string keys into an equivalent SoyMap.
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
 * @param {!SoyMap<K, VIn>} map The map over which to iterate.
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

exports = {
  $$legacyObjectMapToMap,
  $$transformValues,
};
