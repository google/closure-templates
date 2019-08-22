/**
 * @fileoverview
 *
 * Setters for optional Soy idom skip handlers. This is for code
 * that needs to run in hybrid idom + non-idom runtime. This allows setting
 * a skip handler if available in the Idom runtime.
 */
goog.module('google3.javascript.template.soy.skiphandler');
var module = module || { id: 'javascript/template/soy/skiphandler.js' };
/**
 * Setter for skip handler
 * @param el Dom node that is the root of a Soy element. The DOM node should be
 *           an {element} even if Incremental DOM isn't being used.
 * @param fn A function that corresponds to the skip handler of the Soy element.
 *           Because this is to be used in contexts without Incremental DOM,
 *           there is some loss in type information.
 * T should correspond to the corresponding interface for the Soy element.
 */
function setSkipHandler(el, fn) {
    el.__soy_skip_handler = fn;
}
exports.setSkipHandler = setSkipHandler;
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic2tpcGhhbmRsZXIuanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi8uLi8uLi8uLi8uLi8uLi9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveS9za2lwaGFuZGxlci50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiQUFBQTs7Ozs7O0dBTUc7OztBQVFIOzs7Ozs7OztHQVFHO0FBQ0gsU0FBZ0IsY0FBYyxDQUMxQixFQUFXLEVBQUUsRUFBb0M7SUFDbkQsRUFBRSxDQUFDLGtCQUFrQixHQUFHLEVBQUUsQ0FBQztBQUM3QixDQUFDO0FBSEQsd0NBR0MiLCJzb3VyY2VzQ29udGVudCI6WyIvKipcbiAqIEBmaWxlb3ZlcnZpZXdcbiAqXG4gKiBTZXR0ZXJzIGZvciBvcHRpb25hbCBTb3kgaWRvbSBza2lwIGhhbmRsZXJzLiBUaGlzIGlzIGZvciBjb2RlXG4gKiB0aGF0IG5lZWRzIHRvIHJ1biBpbiBoeWJyaWQgaWRvbSArIG5vbi1pZG9tIHJ1bnRpbWUuIFRoaXMgYWxsb3dzIHNldHRpbmdcbiAqIGEgc2tpcCBoYW5kbGVyIGlmIGF2YWlsYWJsZSBpbiB0aGUgSWRvbSBydW50aW1lLlxuICovXG5cbmRlY2xhcmUgZ2xvYmFsIHtcbiAgaW50ZXJmYWNlIE5vZGUge1xuICAgIF9fc295X3NraXBfaGFuZGxlcjogKDxUPihwcmV2OiBULCBuZXh0OiBUKSA9PiBib29sZWFuKXx1bmRlZmluZWQ7XG4gIH1cbn1cblxuLyoqXG4gKiBTZXR0ZXIgZm9yIHNraXAgaGFuZGxlclxuICogQHBhcmFtIGVsIERvbSBub2RlIHRoYXQgaXMgdGhlIHJvb3Qgb2YgYSBTb3kgZWxlbWVudC4gVGhlIERPTSBub2RlIHNob3VsZCBiZVxuICogICAgICAgICAgIGFuIHtlbGVtZW50fSBldmVuIGlmIEluY3JlbWVudGFsIERPTSBpc24ndCBiZWluZyB1c2VkLlxuICogQHBhcmFtIGZuIEEgZnVuY3Rpb24gdGhhdCBjb3JyZXNwb25kcyB0byB0aGUgc2tpcCBoYW5kbGVyIG9mIHRoZSBTb3kgZWxlbWVudC5cbiAqICAgICAgICAgICBCZWNhdXNlIHRoaXMgaXMgdG8gYmUgdXNlZCBpbiBjb250ZXh0cyB3aXRob3V0IEluY3JlbWVudGFsIERPTSxcbiAqICAgICAgICAgICB0aGVyZSBpcyBzb21lIGxvc3MgaW4gdHlwZSBpbmZvcm1hdGlvbi5cbiAqIFQgc2hvdWxkIGNvcnJlc3BvbmQgdG8gdGhlIGNvcnJlc3BvbmRpbmcgaW50ZXJmYWNlIGZvciB0aGUgU295IGVsZW1lbnQuXG4gKi9cbmV4cG9ydCBmdW5jdGlvbiBzZXRTa2lwSGFuZGxlcihcbiAgICBlbDogRWxlbWVudCwgZm46IDxUPihwcmV2OiBULCBuZXh0OiBUKSA9PiBib29sZWFuKSB7XG4gIGVsLl9fc295X3NraXBfaGFuZGxlciA9IGZuO1xufVxuIl19