/**
 * @fileoverview
 * Contains types and objects necessary for Soy-Idom runtime.
 */

import './skiphandler';

import {assert} from 'goog:goog.asserts';  // from //third_party/javascript/closure/asserts
import {IjData} from 'goog:goog.soy';  // from //third_party/javascript/closure/soy
import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //third_party/javascript/closure/soy:data
import {Logger} from 'goog:soy.velog';  // from //javascript/template/soy:soyutils_velog
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

import {IncrementalDomRenderer, patchOuter, SKIP_TOKEN} from './api_idom';
import {isTaggedForSkip} from './global';

/** Function that executes Idom instructions */
export type PatchFunction = (a?: unknown) => void;

/**
 * Function that executes before a patch and determines whether to proceed. If
 * the return value is true, the element is skipped, otherwise it is rendered.
 */
export type SkipHandler = <T>(prev: T, next: T) => boolean;

/**  Getter for skip handler */
function getSkipHandler(el: HTMLElement) {
  return el.__soy_skip_handler;
}


/** Base class for a Soy element. */
export abstract class SoyElement<TData extends {}|null, TInterface extends {}> {
  // Node in which this object is stashed.
  private node: HTMLElement|null = null;
  private skipHandler:
      ((prev: TInterface, next: TInterface) => boolean)|null = null;
  private patchHandler:
      ((prev: TInterface, next: TInterface) => void)|null = null;
  private syncState = true;
  private logger: Logger|null = null;
  // Marker so that future element accesses can find this Soy element from the
  // DOM
  key: string = '';

  constructor(protected data: TData, protected ijData?: IjData) {}

  /**
   * Sets the Logger instance to use for renders of this SoyElement. If `render`
   * is called with a Renderer that has its own Logger, Renderer's Logger is
   * used instead.
   */
  setLogger(logger: Logger|null): this {
    this.logger = logger;
    return this;
  }

  /**
   * State variables that are derived from parameters will continue to be
   * derived until this method is called.
   */
  setSyncState(syncState: boolean) {
    this.syncState = syncState;
  }

  protected shouldSyncState() {
    return this.syncState;
  }

  /**
   * Patches the current dom node.
   * @param renderer Allows injecting a subclass of IncrementalDomRenderer
   *                 to customize the behavior of patches.
   */
  render(renderer = new IncrementalDomRenderer()) {
    assert(this.node);
    if (this.logger && !renderer.getLogger()) {
      renderer.setLogger(this.logger);
    }
    if (this.patchHandler) {
      const patchHandler =
          (this as SoyElement<TData, TInterface>).patchHandler!;
      this.node!.__soy_patch_handler = () => {
        patchHandler(
            this as unknown as TInterface, this as unknown as TInterface);
      };
    }
    const origSyncState = this.syncState;
    this.syncState = false;
    // It is possible that this Soy element has a skip handler on it. When
    // render() is called, ignore the skip handler.
    const skipHandler = this.skipHandler;
    this.skipHandler = null;
    try {
      patchOuter(this.node!, () => {
        // If there are parameters, they must already be specified.
        this.renderInternal(renderer, this.data!);
      });
    } finally {
      this.syncState = origSyncState;
      this.skipHandler = skipHandler;
    }
    if (this.logger) {
      this.logger.resetBuilder();
    }
  }

  /**
   * Replaces the next open call such that it executes Soy element runtime
   * and then replaces itself with the old variant. This relies on compile
   * time validation that the Soy element contains a single open/close tag.
   */
  queueSoyElement(renderer: IncrementalDomRenderer, data: TData) {
    const oldOpen = renderer.open;
    renderer.open = (nameOrCtor: string, key = ''): HTMLElement|void => {
      const el = incrementaldom.open(nameOrCtor, renderer.getNewKey(key));
      renderer.open = oldOpen;
      const maybeSkip = this.handleSoyElementRuntime(el, data);
      if (!maybeSkip) {
        renderer.visit(el);
        return el;
      }
      // This token is passed to ./api_idom.maybeSkip to indicate skipping.
      return SKIP_TOKEN as HTMLElement;
    };
  }

  /**
   * Handles synchronization between the Soy element stashed in the DOM and
   * new data to decide if skipping should happen. Invoked when rendering the
   * open element of a template.
   */
  protected handleSoyElementRuntime(node: HTMLElement|undefined, data: TData):
      boolean {
    /**
     * This is null because it is possible that no DOM has been generated
     * for this Soy element
     * (see http://go/soy/reference/velog#the-logonly-attribute)
     */
    if (!node) {
      return false;
    }
    this.node = node;
    node.__soy = this as unknown as SoyElement<{}, {}>;
    const maybeSkipHandler = this.skipHandler || getSkipHandler(node);
    const newNode = new (
        this.constructor as
        {new (a: TData): SoyElement<TData, TInterface>})(data);
    if (maybeSkipHandler || this.patchHandler) {
      // Users may configure a skip handler to avoid patching DOM in certain
      // cases.
      const oldData = this.data;
      if (maybeSkipHandler) {
        assert(
            !this.skipHandler || !getSkipHandler(node),
            'Do not set skip handlers twice.');
        const skipHandler = maybeSkipHandler;
        if (skipHandler(
                this as unknown as TInterface,
                newNode as unknown as TInterface)) {
          this.data = newNode.data;
          return true;
        }
      }

      if (this.patchHandler) {
        const oldNode = new (
            this.constructor as
            {new (a: TData): SoyElement<TData, TInterface>})(oldData);
        const patchHandler = this.patchHandler;
        this.node.__soy_patch_handler = () => {
          patchHandler(
              oldNode as unknown as TInterface,
              newNode as unknown as TInterface);
        };
      }
    }
    // For server-side rehydration, it is only necessary to execute idom to
    // this point.
    if (isTaggedForSkip(node)) {
      return true;
    }
    this.data = newNode.data;
    return false;
  }

  /**
   * Sets the skip handler.
   *
   * The given function return value means:
   *
   *   - true: skip the element
   *   - false: renders the element
   */
  setSkipHandler(skipHandler: (prev: TInterface, next: TInterface) => boolean) {
    assert(!this.skipHandler, 'Only one skip handler is allowed.');
    this.skipHandler = skipHandler;
  }

  setAfterPatch(handler: (prev: TInterface, next: TInterface) => void) {
    assert(!this.patchHandler, 'Only one patch handler is allowed');
    this.patchHandler = handler;
  }

  /**
   * Makes idom patch calls, inside of a patch context.
   * This returns true if the skip handler runs (after initial render) and
   * returns true.
   */
  abstract renderInternal(renderer: IncrementalDomRenderer, data: TData):
      boolean;
}

/**
 * Type for transforming idom functions into functions that can be coerced
 * to strings.
 */
export interface IdomFunction {
  invoke: (idom: IncrementalDomRenderer) => void;
  isInvokableFn: boolean;
  contentKind: SanitizedContentKind;
  toString: (renderer?: IncrementalDomRenderer) => string;
  toBoolean: () => boolean;
}
