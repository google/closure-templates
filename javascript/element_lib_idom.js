/**
 *
 * @fileoverview
 * Contains types and objects necessary for Soy-Idom runtime.
 *
 * @suppress {checkTypes,constantProperty,extraRequire,missingOverride,missingReturn,unusedPrivateMembers,uselessCode}
 * checked by tsc
 */
goog.module('google3.javascript.template.soy.element_lib_idom');
var module =
    module || {id: 'javascript/template/soy/element_lib_idom.closure.js'};
module = module;
exports = {};
const tsickle_asserts_1 = goog.requireType('goog.asserts');
const tsickle_soy_2 = goog.requireType('goog.soy');
const tsickle_SanitizedContentKind_3 = goog.requireType('goog.soy.data.SanitizedContentKind');
const tsickle_goog_soydata_4 = goog.requireType('soydata');
const tsickle_incremental_dom_5 = goog.requireType('incrementaldom');
const tsickle_api_idom_6 = goog.requireType('google3.javascript.template.soy.api_idom');
const tsickle_global_7 = goog.requireType('google3.javascript.template.soy.global');
const goog_goog_asserts_1 = goog.require('goog.asserts');  // from //javascript/closure/asserts
// from //javascript/template/soy:soy_usegoog_js
const incrementaldom = goog.require('incrementaldom');  // from //third_party/javascript/incremental_dom:incrementaldom
// from //third_party/javascript/incremental_dom:incrementaldom
const api_idom_1 = goog.require('google3.javascript.template.soy.api_idom');
const global_1 = goog.require('google3.javascript.template.soy.global');
/**
 * Function that executes Idom instructions
 * @typedef {function(*=): void}
 */
exports.PatchFunction;
/**
 * Function that executes before a patch and determines whether to proceed.
 * @typedef {function(?, ?): boolean}
 */
exports.SkipHandler;
/**
 * Base class for a Soy element.
 * @abstract
 * @template TData, TInterface
 */
class SoyElement {
  /**
   * @param {TData} data
   * @param {(undefined|!tsickle_soy_2.IjData)=} ijData
   */
  constructor(data, ijData) {
    this.data = data;
    this.ijData = ijData;
    // Node in which this object is stashed.
    this.node = null;
    this.skipHandler = null;
    this.syncState = true;
  }
  /**
   * State variables that are derived from parameters will continue to be
   * derived until this method is called.
   * @param {boolean} syncState
   * @return {void}
   */
  setSyncState(syncState) {
    this.syncState = syncState;
  }
  /**
   * @protected
   * @return {boolean}
   */
  shouldSyncState() {
    return this.syncState;
  }
  /**
   * Patches the current dom node.
   * @param {!tsickle_api_idom_6.IncrementalDomRenderer=} renderer Allows
   *     injecting a subclass of IncrementalDomRenderer
   *                 to customize the behavior of patches.
   * @return {void}
   */
  render(renderer = new api_idom_1.IncrementalDomRenderer()) {
    goog_goog_asserts_1.assert(this.node);
    api_idom_1.patchOuter(
        (/** @type {!HTMLElement} */ (this.node)),
        (/**
          * @return {void}
          */
         () => {
           // If there are parameters, they must already be specified.
           this.renderInternal(renderer, (/** @type {?} */ (this.data)), true);
         }));
  }
  /**
   * Stores the given node in this element object. Invoked (in the
   * generated idom JS) when rendering the open element of a template.
   * @protected
   * @param {(undefined|!HTMLElement)} node
   * @return {(undefined|boolean)}
   */
  setNodeInternal(node) {
    if (!node) {
      return;
    }
    this.node = node;
    // tslint:disable-next-line:no-any
    ((/** @type {?} */ (node))).__soy = this;
    return global_1.isTaggedForSkip(node);
  }
  /**
   * @param {function(TInterface, TInterface): boolean} skipHandler
   * @return {void}
   */
  setSkipHandler(skipHandler) {
    goog_goog_asserts_1.assert(
        !this.skipHandler, 'Only one skip handler is allowed.');
    this.skipHandler = skipHandler;
  }
  /**
   * Makes idom patch calls, inside of a patch context.
   * This returns true if the skip handler runs (after initial render) and
   * returns true.
   * @protected
   * @param {!tsickle_api_idom_6.IncrementalDomRenderer} renderer
   * @param {TData} data
   * @param {boolean=} ignoreSkipHandler
   * @return {boolean}
   */
  renderInternal(renderer, data, ignoreSkipHandler = false) {
    /** @type {!SoyElement} */
    const newNode = new ((/** @type {function(new:SoyElement, TData)} */ (
        this.constructor)))(data);
    if (!ignoreSkipHandler && this.node && this.skipHandler &&
        this.skipHandler(
            (/** @type {TInterface} */ ((/** @type {*} */ (this)))),
            (/** @type {TInterface} */ ((/** @type {*} */ (newNode)))))) {
      this.data = newNode.data;
      // This skips over the current node.
      renderer.alignWithDOM(
          this.node.localName,
          (/** @type {string} */ (incrementaldom.getKey(this.node))));
      return true;
    }
    this.data = newNode.data;
    return false;
  }
}
exports.SoyElement = SoyElement;
if (false) {
  /**
   * @type {(null|!HTMLElement)}
   * @private
   */
  SoyElement.prototype.node;
  /**
   * @type {(null|function(TInterface, TInterface): boolean)}
   * @private
   */
  SoyElement.prototype.skipHandler;
  /**
   * @type {boolean}
   * @private
   */
  SoyElement.prototype.syncState;
  /**
   * @type {TData}
   * @protected
   */
  SoyElement.prototype.data;
  /**
   * @type {(undefined|!tsickle_soy_2.IjData)}
   * @protected
   */
  SoyElement.prototype.ijData;
}
/**
 * Type for transforming idom functions into functions that can be coerced
 * to strings.
 * @record
 * @extends {tsickle_goog_soydata_4.IdomFunctionMembers}
 */
function IdomFunction() {}
exports.IdomFunction = IdomFunction;
if (false) {
  /** @type {?} */
  IdomFunction.prototype.contentKind;
  /**
   * @type {function((undefined|!tsickle_api_idom_6.IncrementalDomRenderer)=):
   *     string}
   */
  IdomFunction.prototype.toString;
  /** @type {function(): boolean} */
  IdomFunction.prototype.toBoolean;
  /* Skipping unhandled member: (idom: IncrementalDomRenderer): void;*/
}
//#
// sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZWxlbWVudF9saWJfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L2VsZW1lbnRfbGliX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6Ijs7Ozs7Ozs7Ozs7Ozs7Ozs7O0FBS0EseURBQXlDLENBQUUsb0NBQW9DOztBQUkvRSw0RkFBaUQsQ0FBRSwrREFBK0Q7O0FBRWxILDRFQUE4RDtBQUM5RCx3RUFBeUM7Ozs7O0FBR3pDLHNCQUFrRDs7Ozs7QUFHbEQsb0JBQTJEOzs7Ozs7QUFHM0QsTUFBc0IsVUFBVTs7Ozs7SUFPOUIsWUFBc0IsSUFBVyxFQUFZLE1BQWU7UUFBdEMsU0FBSSxHQUFKLElBQUksQ0FBTztRQUFZLFdBQU0sR0FBTixNQUFNLENBQVM7O1FBTHBELFNBQUksR0FBcUIsSUFBSSxDQUFDO1FBQzlCLGdCQUFXLEdBQzBDLElBQUksQ0FBQztRQUMxRCxjQUFTLEdBQUcsSUFBSSxDQUFDO0lBRXNDLENBQUM7Ozs7Ozs7SUFNaEUsWUFBWSxDQUFDLFNBQWtCO1FBQzdCLElBQUksQ0FBQyxTQUFTLEdBQUcsU0FBUyxDQUFDO0lBQzdCLENBQUM7Ozs7O0lBRVMsZUFBZTtRQUN2QixPQUFPLElBQUksQ0FBQyxTQUFTLENBQUM7SUFDeEIsQ0FBQzs7Ozs7OztJQU9ELE1BQU0sQ0FBQyxRQUFRLEdBQUcsSUFBSSxpQ0FBc0IsRUFBRTtRQUM1QywwQkFBTSxDQUFDLElBQUksQ0FBQyxJQUFJLENBQUMsQ0FBQztRQUNsQixxQkFBVSxDQUFDLDhCQUFBLElBQUksQ0FBQyxJQUFJLEVBQUM7OztRQUFFLEdBQUcsRUFBRTtZQUMxQiwyREFBMkQ7WUFDM0QsSUFBSSxDQUFDLGNBQWMsQ0FBQyxRQUFRLEVBQUUsbUJBQUEsSUFBSSxDQUFDLElBQUksRUFBQyxFQUFFLElBQUksQ0FBQyxDQUFDO1FBQ2xELENBQUMsRUFBQyxDQUFDO0lBQ0wsQ0FBQzs7Ozs7Ozs7SUFNUyxlQUFlLENBQUMsSUFBMkI7UUFDbkQ7Ozs7V0FJRztRQUNILElBQUksQ0FBQyxJQUFJLEVBQUU7WUFDVCxPQUFPO1NBQ1I7UUFDRCxJQUFJLENBQUMsSUFBSSxHQUFHLElBQUksQ0FBQztRQUNqQixrQ0FBa0M7UUFDbEMsQ0FBQyxtQkFBQSxJQUFJLEVBQU8sQ0FBQyxDQUFDLEtBQUssR0FBRyxJQUFJLENBQUM7UUFDM0IsT0FBTyx3QkFBZSxDQUFDLElBQUksQ0FBQyxDQUFDO0lBQy9CLENBQUM7Ozs7O0lBRUQsY0FBYyxDQUFDLFdBQTREO1FBQ3pFLDBCQUFNLENBQUMsQ0FBQyxJQUFJLENBQUMsV0FBVyxFQUFFLG1DQUFtQyxDQUFDLENBQUM7UUFDL0QsSUFBSSxDQUFDLFdBQVcsR0FBRyxXQUFXLENBQUM7SUFDakMsQ0FBQzs7Ozs7Ozs7Ozs7SUFPUyxjQUFjLENBQ3BCLFFBQWdDLEVBQUUsSUFBVyxFQUM3QyxpQkFBaUIsR0FBRyxLQUFLOztjQUNyQixPQUFPLEdBQUcsSUFBSSxDQUNoQixpREFBQSxJQUFJLENBQUMsV0FBVyxFQUMrQixDQUFDLENBQUMsSUFBSSxDQUFDO1FBQzFELElBQUksQ0FBQyxpQkFBaUIsSUFBSSxJQUFJLENBQUMsSUFBSSxJQUFJLElBQUksQ0FBQyxXQUFXO1lBQ25ELElBQUksQ0FBQyxXQUFXLENBQ1osNEJBQUEsbUJBQUEsSUFBSSxFQUFXLEVBQWMsRUFBRSw0QkFBQSxtQkFBQSxPQUFPLEVBQVcsRUFBYyxDQUFDLEVBQUU7WUFDeEUsSUFBSSxDQUFDLElBQUksR0FBRyxPQUFPLENBQUMsSUFBSSxDQUFDO1lBQ3pCLG9DQUFvQztZQUNwQyxRQUFRLENBQUMsWUFBWSxDQUNqQixJQUFJLENBQUMsSUFBSSxDQUFDLFNBQVMsRUFBRSx3QkFBQSxjQUFjLENBQUMsTUFBTSxDQUFDLElBQUksQ0FBQyxJQUFJLENBQUMsRUFBVSxDQUFDLENBQUM7WUFDckUsT0FBTyxJQUFJLENBQUM7U0FDYjtRQUNELElBQUksQ0FBQyxJQUFJLEdBQUcsT0FBTyxDQUFDLElBQUksQ0FBQztRQUN6QixPQUFPLEtBQUssQ0FBQztJQUNmLENBQUM7Q0FDRjtBQWpGRCxnQ0FpRkM7Ozs7OztJQS9FQywwQkFBc0M7Ozs7O0lBQ3RDLGlDQUNrRTs7Ozs7SUFDbEUsK0JBQXlCOzs7OztJQUViLDBCQUFxQjs7Ozs7SUFBRSw0QkFBeUI7Ozs7Ozs7O0FBZ0Y5RCwyQkFLQzs7OztJQUhDLG1DQUFrQzs7SUFDbEMsZ0NBQXdEOztJQUN4RCxpQ0FBeUIiLCJzb3VyY2VzQ29udGVudCI6WyIvKipcbiAqIEBmaWxlb3ZlcnZpZXdcbiAqIENvbnRhaW5zIHR5cGVzIGFuZCBvYmplY3RzIG5lY2Vzc2FyeSBmb3IgU295LUlkb20gcnVudGltZS5cbiAqL1xuXG5pbXBvcnQge2Fzc2VydH0gZnJvbSAnZ29vZzpnb29nLmFzc2VydHMnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9hc3NlcnRzXG5pbXBvcnQge0lqRGF0YX0gZnJvbSAnZ29vZzpnb29nLnNveSc7ICAgICAgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3lcbmltcG9ydCBTYW5pdGl6ZWRDb250ZW50S2luZCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkQ29udGVudEtpbmQnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQge0lkb21GdW5jdGlvbk1lbWJlcnN9IGZyb20gJ2dvb2c6c295ZGF0YSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC90ZW1wbGF0ZS9zb3k6c295X3VzZWdvb2dfanNcbmltcG9ydCAqIGFzIGluY3JlbWVudGFsZG9tIGZyb20gJ2luY3JlbWVudGFsZG9tJzsgIC8vIGZyb20gLy90aGlyZF9wYXJ0eS9qYXZhc2NyaXB0L2luY3JlbWVudGFsX2RvbTppbmNyZW1lbnRhbGRvbVxuXG5pbXBvcnQge0luY3JlbWVudGFsRG9tUmVuZGVyZXIsIHBhdGNoT3V0ZXJ9IGZyb20gJy4vYXBpX2lkb20nO1xuaW1wb3J0IHtpc1RhZ2dlZEZvclNraXB9IGZyb20gJy4vZ2xvYmFsJztcblxuLyoqIEZ1bmN0aW9uIHRoYXQgZXhlY3V0ZXMgSWRvbSBpbnN0cnVjdGlvbnMgKi9cbmV4cG9ydCB0eXBlIFBhdGNoRnVuY3Rpb24gPSAoYT86IHVua25vd24pID0+IHZvaWQ7XG5cbi8qKiBGdW5jdGlvbiB0aGF0IGV4ZWN1dGVzIGJlZm9yZSBhIHBhdGNoIGFuZCBkZXRlcm1pbmVzIHdoZXRoZXIgdG8gcHJvY2VlZC4gKi9cbmV4cG9ydCB0eXBlIFNraXBIYW5kbGVyID0gPFQ+KHByZXY6IFQsIG5leHQ6IFQpID0+IGJvb2xlYW47XG5cbi8qKiBCYXNlIGNsYXNzIGZvciBhIFNveSBlbGVtZW50LiAqL1xuZXhwb3J0IGFic3RyYWN0IGNsYXNzIFNveUVsZW1lbnQ8VERhdGEgZXh0ZW5kcyB7fXxudWxsLCBUSW50ZXJmYWNlIGV4dGVuZHMge30+IHtcbiAgLy8gTm9kZSBpbiB3aGljaCB0aGlzIG9iamVjdCBpcyBzdGFzaGVkLlxuICBwcml2YXRlIG5vZGU6IEhUTUxFbGVtZW50fG51bGwgPSBudWxsO1xuICBwcml2YXRlIHNraXBIYW5kbGVyOlxuICAgICAgKChwcmV2OiBUSW50ZXJmYWNlLCBuZXh0OiBUSW50ZXJmYWNlKSA9PiBib29sZWFuKXxudWxsID0gbnVsbDtcbiAgcHJpdmF0ZSBzeW5jU3RhdGUgPSB0cnVlO1xuXG4gIGNvbnN0cnVjdG9yKHByb3RlY3RlZCBkYXRhOiBURGF0YSwgcHJvdGVjdGVkIGlqRGF0YT86IElqRGF0YSkge31cblxuICAvKipcbiAgICogU3RhdGUgdmFyaWFibGVzIHRoYXQgYXJlIGRlcml2ZWQgZnJvbSBwYXJhbWV0ZXJzIHdpbGwgY29udGludWUgdG8gYmVcbiAgICogZGVyaXZlZCB1bnRpbCB0aGlzIG1ldGhvZCBpcyBjYWxsZWQuXG4gICAqL1xuICBzZXRTeW5jU3RhdGUoc3luY1N0YXRlOiBib29sZWFuKSB7XG4gICAgdGhpcy5zeW5jU3RhdGUgPSBzeW5jU3RhdGU7XG4gIH1cblxuICBwcm90ZWN0ZWQgc2hvdWxkU3luY1N0YXRlKCkge1xuICAgIHJldHVybiB0aGlzLnN5bmNTdGF0ZTtcbiAgfVxuXG4gIC8qKlxuICAgKiBQYXRjaGVzIHRoZSBjdXJyZW50IGRvbSBub2RlLlxuICAgKiBAcGFyYW0gcmVuZGVyZXIgQWxsb3dzIGluamVjdGluZyBhIHN1YmNsYXNzIG9mIEluY3JlbWVudGFsRG9tUmVuZGVyZXJcbiAgICogICAgICAgICAgICAgICAgIHRvIGN1c3RvbWl6ZSB0aGUgYmVoYXZpb3Igb2YgcGF0Y2hlcy5cbiAgICovXG4gIHJlbmRlcihyZW5kZXJlciA9IG5ldyBJbmNyZW1lbnRhbERvbVJlbmRlcmVyKCkpIHtcbiAgICBhc3NlcnQodGhpcy5ub2RlKTtcbiAgICBwYXRjaE91dGVyKHRoaXMubm9kZSEsICgpID0+IHtcbiAgICAgIC8vIElmIHRoZXJlIGFyZSBwYXJhbWV0ZXJzLCB0aGV5IG11c3QgYWxyZWFkeSBiZSBzcGVjaWZpZWQuXG4gICAgICB0aGlzLnJlbmRlckludGVybmFsKHJlbmRlcmVyLCB0aGlzLmRhdGEhLCB0cnVlKTtcbiAgICB9KTtcbiAgfVxuXG4gIC8qKlxuICAgKiBTdG9yZXMgdGhlIGdpdmVuIG5vZGUgaW4gdGhpcyBlbGVtZW50IG9iamVjdC4gSW52b2tlZCAoaW4gdGhlXG4gICAqIGdlbmVyYXRlZCBpZG9tIEpTKSB3aGVuIHJlbmRlcmluZyB0aGUgb3BlbiBlbGVtZW50IG9mIGEgdGVtcGxhdGUuXG4gICAqL1xuICBwcm90ZWN0ZWQgc2V0Tm9kZUludGVybmFsKG5vZGU6IEhUTUxFbGVtZW50fHVuZGVmaW5lZCkge1xuICAgIC8qKlxuICAgICAqIFRoaXMgaXMgbnVsbCBiZWNhdXNlIGl0IGlzIHBvc3NpYmxlIHRoYXQgbm8gRE9NIGhhcyBiZWVuIGdlbmVyYXRlZFxuICAgICAqIGZvciB0aGlzIFNveSBlbGVtZW50XG4gICAgICogKHNlZSBodHRwOi8vZ28vc295L3JlZmVyZW5jZS92ZWxvZyN0aGUtbG9nb25seS1hdHRyaWJ1dGUpXG4gICAgICovXG4gICAgaWYgKCFub2RlKSB7XG4gICAgICByZXR1cm47XG4gICAgfVxuICAgIHRoaXMubm9kZSA9IG5vZGU7XG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIChub2RlIGFzIGFueSkuX19zb3kgPSB0aGlzO1xuICAgIHJldHVybiBpc1RhZ2dlZEZvclNraXAobm9kZSk7XG4gIH1cblxuICBzZXRTa2lwSGFuZGxlcihza2lwSGFuZGxlcjogKHByZXY6IFRJbnRlcmZhY2UsIG5leHQ6IFRJbnRlcmZhY2UpID0+IGJvb2xlYW4pIHtcbiAgICBhc3NlcnQoIXRoaXMuc2tpcEhhbmRsZXIsICdPbmx5IG9uZSBza2lwIGhhbmRsZXIgaXMgYWxsb3dlZC4nKTtcbiAgICB0aGlzLnNraXBIYW5kbGVyID0gc2tpcEhhbmRsZXI7XG4gIH1cblxuICAvKipcbiAgICogTWFrZXMgaWRvbSBwYXRjaCBjYWxscywgaW5zaWRlIG9mIGEgcGF0Y2ggY29udGV4dC5cbiAgICogVGhpcyByZXR1cm5zIHRydWUgaWYgdGhlIHNraXAgaGFuZGxlciBydW5zIChhZnRlciBpbml0aWFsIHJlbmRlcikgYW5kXG4gICAqIHJldHVybnMgdHJ1ZS5cbiAgICovXG4gIHByb3RlY3RlZCByZW5kZXJJbnRlcm5hbChcbiAgICAgIHJlbmRlcmVyOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBkYXRhOiBURGF0YSxcbiAgICAgIGlnbm9yZVNraXBIYW5kbGVyID0gZmFsc2UpIHtcbiAgICBjb25zdCBuZXdOb2RlID0gbmV3IChcbiAgICAgICAgdGhpcy5jb25zdHJ1Y3RvciBhc1xuICAgICAgICB7bmV3IChhOiBURGF0YSk6IFNveUVsZW1lbnQ8VERhdGEsIFRJbnRlcmZhY2U+fSkoZGF0YSk7XG4gICAgaWYgKCFpZ25vcmVTa2lwSGFuZGxlciAmJiB0aGlzLm5vZGUgJiYgdGhpcy5za2lwSGFuZGxlciAmJlxuICAgICAgICB0aGlzLnNraXBIYW5kbGVyKFxuICAgICAgICAgICAgdGhpcyBhcyB1bmtub3duIGFzIFRJbnRlcmZhY2UsIG5ld05vZGUgYXMgdW5rbm93biBhcyBUSW50ZXJmYWNlKSkge1xuICAgICAgdGhpcy5kYXRhID0gbmV3Tm9kZS5kYXRhO1xuICAgICAgLy8gVGhpcyBza2lwcyBvdmVyIHRoZSBjdXJyZW50IG5vZGUuXG4gICAgICByZW5kZXJlci5hbGlnbldpdGhET00oXG4gICAgICAgICAgdGhpcy5ub2RlLmxvY2FsTmFtZSwgaW5jcmVtZW50YWxkb20uZ2V0S2V5KHRoaXMubm9kZSkgYXMgc3RyaW5nKTtcbiAgICAgIHJldHVybiB0cnVlO1xuICAgIH1cbiAgICB0aGlzLmRhdGEgPSBuZXdOb2RlLmRhdGE7XG4gICAgcmV0dXJuIGZhbHNlO1xuICB9XG59XG5cbi8qKlxuICogVHlwZSBmb3IgdHJhbnNmb3JtaW5nIGlkb20gZnVuY3Rpb25zIGludG8gZnVuY3Rpb25zIHRoYXQgY2FuIGJlIGNvZXJjZWRcbiAqIHRvIHN0cmluZ3MuXG4gKi9cbmV4cG9ydCBpbnRlcmZhY2UgSWRvbUZ1bmN0aW9uIGV4dGVuZHMgSWRvbUZ1bmN0aW9uTWVtYmVycyB7XG4gIChpZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyKTogdm9pZDtcbiAgY29udGVudEtpbmQ6IFNhbml0aXplZENvbnRlbnRLaW5kO1xuICB0b1N0cmluZzogKHJlbmRlcmVyPzogSW5jcmVtZW50YWxEb21SZW5kZXJlcikgPT4gc3RyaW5nO1xuICB0b0Jvb2xlYW46ICgpID0+IGJvb2xlYW47XG59XG4iXX0=