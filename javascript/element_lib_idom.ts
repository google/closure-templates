/**
 * @fileoverview
 * Contains types and objects necessary for Soy-Idom runtime.
 */

import './skiphandler';

import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //third_party/javascript/closure/soy:data
import {Logger} from 'google3/javascript/template/soy/soyutils_velog';
import {assert, assertExists} from 'google3/third_party/javascript/closure/asserts/asserts';
import {IDisposable} from 'google3/third_party/javascript/closure/disposable/idisposable';

import {IncrementalDomRenderer, patchOuter} from './api_idom';
import {getGlobalSkipHandler, isTaggedForSkip} from './global';
import {IdomTemplate, IjData} from './templates';

/**
 * An HTML or attributes idom callback from a `soy.idom.js` file.
 *
 * These callbacks are never exposed directly; they're wrapped in IdomFunction.
 */
export type PatchFunction = (a: IncrementalDomRenderer) => void;

/**
 * Function that executes before a patch and determines whether to proceed. If
 * the return value is true, the element is skipped, otherwise it is rendered.
 */
export type SkipHandler = <T>(prev: T, next: T) => boolean;

/** Gets a skip handler that was passed to setSkipHandler. */
export function getSkipHandler(el: HTMLElement) {
  return el.__soy_skip_handler;
}

/** Base class for a Soy element. */
export abstract class SoyElement<TData extends {}|null, TInterface extends {}>
    implements IDisposable {
  // Node in which this object is stashed.
  node: HTMLElement|null = null;
  private skipHandler:
      ((prev: TInterface, next: TInterface) => boolean)|null = null;
  private patchHandler:
      ((prev: TInterface, next: TInterface) => void)|null = null;
  private syncState = true;
  private loggerPrivate: Logger|null = null;
  // Marker so that future element accesses can find this Soy element from the
  // DOM
  key: string = '';
  private logGraft = false;
  private disposed = false;
  data!: TData;
  ijData!: IjData;
  // Setting this to TData makes this type invariant.
  template!: IdomTemplate<unknown>;

  dispose() {
    if (!this.disposed) {
      this.disposed = true;
      this.unsetLifecycleHooks();
    }
  }

  isDisposed() {
    return this.disposed;
  }

  /**
   * Sets the Logger instance to use for renders of this SoyElement. If `render`
   * is called with a Renderer that has its own Logger, Renderer's Logger is
   * used instead.
   */
  setLogger(logger: Logger|null): this {
    this.loggerPrivate = logger;
    return this;
  }

  /**
   * Enables or disables automatic log grafting when rendering this SoyElement.
   */
  setLogGraft(logGraft: boolean): this {
    this.logGraft = logGraft;
    return this;
  }

  /**
   * State variables that are derived from parameters will continue to be
   * derived until this method is called.
   */
  setSyncState(syncState: boolean): this {
    this.syncState = syncState;
    return this;
  }

  protected shouldSyncState() {
    return this.syncState;
  }

  protected syncStateFromData(data: TData) {}

  /**
   * Patches the current dom node.
   * @param renderer Allows injecting a subclass of IncrementalDomRenderer
   *                 to customize the behavior of patches.
   */
  render(renderer = new IncrementalDomRenderer()) {
    assert(this.node);
    if (this.loggerPrivate && !renderer.getLogger()) {
      renderer.setLogger(this.loggerPrivate);
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
        if (this.loggerPrivate && this.logGraft) {
          this.loggerPrivate.logGraft(this.node!, () => {
            this.renderInternal(renderer, this.data!);
          });
        } else {
          this.renderInternal(renderer, this.data!);
        }
      });
    } finally {
      this.syncState = origSyncState;
      this.skipHandler = skipHandler;
    }
    if (this.loggerPrivate) {
      this.loggerPrivate.resetBuilder();
    }
  }

  /**
   * Handles synchronization between the Soy element stashed in the DOM and
   * new data to decide if skipping should happen. Invoked when rendering the
   * open element of a template.
   */
  handleSoyElementRuntime(node: HTMLElement|undefined, data: TData): boolean {
    /**
     * This is null because it is possible that no DOM has been generated
     * for this Soy element
     */
    if (!node) {
      return false;
    }
    this.node = node;
    node.__soy = this as unknown as SoyElement<{}, {}>;
    if (this.shouldSyncState()) {
      this.syncStateFromData(data);
    }
    const maybeSkipHandler = this.skipHandler || getSkipHandler(node);
    const newNode =
        new (this.constructor as {new (): SoyElement<TData, TInterface>})();
    newNode.data = data;
    const globalSkipHandler = getGlobalSkipHandler() as
        unknown as ((prev: TInterface, next: TInterface) => boolean);
    if (globalSkipHandler &&
        globalSkipHandler(
            this as unknown as TInterface, newNode as unknown as TInterface)) {
      this.data = newNode.data;
      return true;
    }
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
        const oldNode =
            new (this.constructor as {new (): SoyElement<TData, TInterface>})();
        oldNode.data = oldData;
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

  unsetLifecycleHooks() {
    this.skipHandler = null;
    this.patchHandler = null;
    const node = assertExists(this.node);
    node.__soy_skip_handler = undefined;
    node.__soy_patch_handler = undefined;
  }

  /**
   * Sets the skip handler.
   * They execute right before a patch and influence whether further patching is
   * needed.
   *
   * The given function return value means:
   *   - true: skip the element
   *   - false: renders the element
   */
  setSkipHandler(skipHandler: (prev: TInterface, next: TInterface) => boolean) {
    assert(!this.skipHandler, 'Only one skip handler is allowed.');
    this.skipHandler = skipHandler;
  }

  /**
   * Indicates whether a skip handler was passed to SoyElement.setSkipHandler or
   * setSkipHandler(node). For internal use.
   */
  hasAnySkipHandler(): boolean {
    // tslint:disable-next-line ban-truthy-function-in-boolean-expression
    return !!this.skipHandler || !!(this.node && getSkipHandler(this.node));
  }

  /**
   * Sets the after patch handler.
   *
   * Executes right after a Soy element has finished rendering, but before
   * anymore of the template executes.
   */
  setAfterPatch(handler: (prev: TInterface, next: TInterface) => void) {
    assert(!this.patchHandler, 'Only one patch handler is allowed');
    this.patchHandler = handler;
  }

  /**
   * Makes idom patch calls, inside of a patch context.
   */
  renderInternal(renderer: IncrementalDomRenderer, data: TData) {
    this.template(renderer, data);
  }
}

/**
 * Type for transforming idom functions into functions that can be coerced
 * to strings.
 */
export interface IdomFunction {
  (idom: IncrementalDomRenderer): void;
  invoke: (idom: IncrementalDomRenderer) => void;
  isInvokableFn: boolean;
  contentKind: SanitizedContentKind;
  toString: (renderer?: IncrementalDomRenderer) => string;
}
