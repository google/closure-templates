/**
 * @fileoverview
 *
 * Setters for optional Soy idom skip handlers. This is for code
 * that needs to run in hybrid idom + non-idom runtime. This allows setting
 * a skip handler if available in the Idom runtime.
 */

// We cannot import SoyElement because this is required to be in its standalone
// library in order to restrict visibility.
// tslint:disable-next-line:no-any
type UnknownSoyElement = any;

declare global {
  interface Node {
    // tslint:disable-next-line:enforce-name-casing
    __soy_skip_handler:
        ((prev: UnknownSoyElement,
          next: UnknownSoyElement) => boolean)|undefined;
    // tslint:disable-next-line:enforce-name-casing
    __soy_patch_handler: (() => void)|undefined;
  }
}

/**
 * Setter for skip handler
 * @param el Dom node that is the root of a Soy element. The DOM node should be
 *           an {element} even if Incremental DOM isn't being used.
 * @param fn A function that corresponds to the skip handler of the Soy element.
 *           Because this is to be used in contexts without Incremental DOM,
 *           there is some loss in type information.
 * T should correspond to the corresponding interface for the Soy element.
 */
export function setSkipHandler(
    el: Element,
    fn: (prev: UnknownSoyElement, next: UnknownSoyElement) => boolean) {
  el.__soy_skip_handler = fn;
}

/**
 * Setter for patch handler
 * @param el Dom node that is the root of a Soy element. The DOM node should be
 *           an {element} even if Incremental DOM isn't being used.
 * @param fn A function that corresponds to the patch handler of the Soy
 *     element.
 */
export function setAfterPatchHandler(el: Element, fn: () => void) {
  el.__soy_patch_handler = fn;
}
