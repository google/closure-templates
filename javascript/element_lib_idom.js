/**
 * @fileoverview
 * Contains types and objects necessary for Soy-Idom runtime.
 * @suppress {lintChecks}
 */
goog.module('google3.javascript.template.soy.element_lib_idom');
var module = module || { id: 'javascript/template/soy/element_lib_idom.js' };
var tsickle_module_1_ = goog.require('google3.javascript.template.soy.skiphandler');
var goog_goog_asserts_1 = goog.require('goog.asserts'); // from //javascript/closure/asserts
var incrementaldom = goog.require('google3.third_party.javascript.incremental_dom.index'); // from //third_party/javascript/incremental_dom:incrementaldom
var api_idom_1 = goog.require('google3.javascript.template.soy.api_idom');
var global_1 = goog.require('google3.javascript.template.soy.global');
/**  Getter for skip handler */
function getSkipHandler(el) {
    return el.__soy_skip_handler;
}
/** Base class for a Soy element. */
var SoyElement = /** @class */ (function () {
    function SoyElement(data, ijData) {
        this.data = data;
        this.ijData = ijData;
        // Node in which this object is stashed.
        this.node = null;
        this.skipHandler = null;
        this.syncState = true;
        // Marker so that future element accesses can find this Soy element from the
        // DOM
        this.key = '';
    }
    /**
     * State variables that are derived from parameters will continue to be
     * derived until this method is called.
     */
    SoyElement.prototype.setSyncState = function (syncState) {
        this.syncState = syncState;
    };
    SoyElement.prototype.shouldSyncState = function () {
        return this.syncState;
    };
    /**
     * Patches the current dom node.
     * @param renderer Allows injecting a subclass of IncrementalDomRenderer
     *                 to customize the behavior of patches.
     */
    SoyElement.prototype.render = function (renderer) {
        var _this = this;
        if (renderer === void 0) { renderer = new api_idom_1.IncrementalDomRenderer(); }
        goog_goog_asserts_1.assert(this.node);
        // It is possible that this Soy element has a skip handler on it. When
        // render() is called, ignore the skip handler.
        var skipHandler = this.skipHandler;
        this.skipHandler = null;
        api_idom_1.patchOuter(this.node, function () {
            // If there are parameters, they must already be specified.
            _this.renderInternal(renderer, _this.data);
        });
        this.skipHandler = skipHandler;
    };
    /**
     * Replaces the next open call such that it executes Soy element runtime
     * and then replaces itself with the old variant. This relies on compile
     * time validation that the Soy element contains a single open/close tag.
     */
    SoyElement.prototype.queueSoyElement = function (renderer, data) {
        var _this = this;
        var oldOpen = renderer.open;
        renderer.open = function (nameOrCtor, key) {
            if (key === void 0) { key = ''; }
            var el = incrementaldom.open(nameOrCtor, renderer.getNewKey(key));
            renderer.open = oldOpen;
            var maybeSkip = _this.handleSoyElementRuntime(el, data);
            if (!maybeSkip) {
                renderer.visit(el);
                return el;
            }
            // This token is passed to ./api_idom.maybeSkip to indicate skipping.
            return api_idom_1.SKIP_TOKEN;
        };
    };
    /**
     * Handles synchronization between the Soy element stashed in the DOM and
     * new data to decide if skipping should happen. Invoked when rendering the
     * open element of a template.
     */
    SoyElement.prototype.handleSoyElementRuntime = function (node, data) {
        if (!node) {
            return false;
        }
        this.node = node;
        node.__soy = this;
        var newNode = new this.constructor(data);
        // Users may configure a skip handler to avoid patching DOM in certain
        // cases.
        var maybeSkipHandler = getSkipHandler(node);
        if (this.skipHandler || maybeSkipHandler) {
            goog_goog_asserts_1.assert(!this.skipHandler || !maybeSkipHandler, 'Do not set skip handlers twice.');
            var skipHandler = this.skipHandler || maybeSkipHandler;
            if (skipHandler(this, newNode)) {
                return true;
            }
        }
        // For server-side rehydration, it is only necessary to execute idom to
        // this point.
        if (global_1.isTaggedForSkip(node)) {
            return true;
        }
        this.data = newNode.data;
        return false;
    };
    SoyElement.prototype.setSkipHandler = function (skipHandler) {
        goog_goog_asserts_1.assert(!this.skipHandler, 'Only one skip handler is allowed.');
        this.skipHandler = skipHandler;
    };
    return SoyElement;
}());
exports.SoyElement = SoyElement;
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZWxlbWVudF9saWJfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L2VsZW1lbnRfbGliX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUE7OztHQUdHOzs7QUFFSCxvRkFBdUI7QUFFdkIsdURBQXlDLENBQUUsb0NBQW9DO0FBSS9FLDBGQUFpRCxDQUFFLCtEQUErRDtBQUVsSCwwRUFBMEU7QUFDMUUsc0VBQXlDO0FBUXpDLCtCQUErQjtBQUMvQixTQUFTLGNBQWMsQ0FBQyxFQUFlO0lBQ3JDLE9BQU8sRUFBRSxDQUFDLGtCQUFrQixDQUFDO0FBQy9CLENBQUM7QUFHRCxvQ0FBb0M7QUFDcEM7SUFVRSxvQkFBc0IsSUFBVyxFQUFZLE1BQWU7UUFBdEMsU0FBSSxHQUFKLElBQUksQ0FBTztRQUFZLFdBQU0sR0FBTixNQUFNLENBQVM7UUFUNUQsd0NBQXdDO1FBQ2hDLFNBQUksR0FBcUIsSUFBSSxDQUFDO1FBQzlCLGdCQUFXLEdBQzBDLElBQUksQ0FBQztRQUMxRCxjQUFTLEdBQUcsSUFBSSxDQUFDO1FBQ3pCLDRFQUE0RTtRQUM1RSxNQUFNO1FBQ04sUUFBRyxHQUFXLEVBQUUsQ0FBQztJQUU4QyxDQUFDO0lBRWhFOzs7T0FHRztJQUNILGlDQUFZLEdBQVosVUFBYSxTQUFrQjtRQUM3QixJQUFJLENBQUMsU0FBUyxHQUFHLFNBQVMsQ0FBQztJQUM3QixDQUFDO0lBRVMsb0NBQWUsR0FBekI7UUFDRSxPQUFPLElBQUksQ0FBQyxTQUFTLENBQUM7SUFDeEIsQ0FBQztJQUVEOzs7O09BSUc7SUFDSCwyQkFBTSxHQUFOLFVBQU8sUUFBdUM7UUFBOUMsaUJBV0M7UUFYTSx5QkFBQSxFQUFBLGVBQWUsaUNBQXNCLEVBQUU7UUFDNUMsMEJBQU0sQ0FBQyxJQUFJLENBQUMsSUFBSSxDQUFDLENBQUM7UUFDbEIsc0VBQXNFO1FBQ3RFLCtDQUErQztRQUMvQyxJQUFNLFdBQVcsR0FBRyxJQUFJLENBQUMsV0FBVyxDQUFDO1FBQ3JDLElBQUksQ0FBQyxXQUFXLEdBQUcsSUFBSSxDQUFDO1FBQ3hCLHFCQUFVLENBQUMsSUFBSSxDQUFDLElBQUssRUFBRTtZQUNyQiwyREFBMkQ7WUFDM0QsS0FBSSxDQUFDLGNBQWMsQ0FBQyxRQUFRLEVBQUUsS0FBSSxDQUFDLElBQUssQ0FBQyxDQUFDO1FBQzVDLENBQUMsQ0FBQyxDQUFDO1FBQ0gsSUFBSSxDQUFDLFdBQVcsR0FBRyxXQUFXLENBQUM7SUFDakMsQ0FBQztJQUVEOzs7O09BSUc7SUFDSCxvQ0FBZSxHQUFmLFVBQWdCLFFBQWdDLEVBQUUsSUFBVztRQUE3RCxpQkFhQztRQVpDLElBQU0sT0FBTyxHQUFHLFFBQVEsQ0FBQyxJQUFJLENBQUM7UUFDOUIsUUFBUSxDQUFDLElBQUksR0FBRyxVQUFDLFVBQWtCLEVBQUUsR0FBUTtZQUFSLG9CQUFBLEVBQUEsUUFBUTtZQUMzQyxJQUFNLEVBQUUsR0FBRyxjQUFjLENBQUMsSUFBSSxDQUFDLFVBQVUsRUFBRSxRQUFRLENBQUMsU0FBUyxDQUFDLEdBQUcsQ0FBQyxDQUFDLENBQUM7WUFDcEUsUUFBUSxDQUFDLElBQUksR0FBRyxPQUFPLENBQUM7WUFDeEIsSUFBTSxTQUFTLEdBQUcsS0FBSSxDQUFDLHVCQUF1QixDQUFDLEVBQUUsRUFBRSxJQUFJLENBQUMsQ0FBQztZQUN6RCxJQUFJLENBQUMsU0FBUyxFQUFFO2dCQUNkLFFBQVEsQ0FBQyxLQUFLLENBQUMsRUFBRSxDQUFDLENBQUM7Z0JBQ25CLE9BQU8sRUFBRSxDQUFDO2FBQ1g7WUFDRCxxRUFBcUU7WUFDckUsT0FBTyxxQkFBeUIsQ0FBQztRQUNuQyxDQUFDLENBQUM7SUFDSixDQUFDO0lBRUQ7Ozs7T0FJRztJQUNPLDRDQUF1QixHQUFqQyxVQUFrQyxJQUEyQixFQUFFLElBQVc7UUFFeEU7Ozs7V0FJRztRQUNILElBQUksQ0FBQyxJQUFJLEVBQUU7WUFDVCxPQUFPLEtBQUssQ0FBQztTQUNkO1FBQ0QsSUFBSSxDQUFDLElBQUksR0FBRyxJQUFJLENBQUM7UUFDakIsSUFBSSxDQUFDLEtBQUssR0FBRyxJQUFxQyxDQUFDO1FBQ25ELElBQU0sT0FBTyxHQUFHLElBQ1osSUFBSSxDQUFDLFdBQzJDLENBQUMsSUFBSSxDQUFDLENBQUM7UUFFM0Qsc0VBQXNFO1FBQ3RFLFNBQVM7UUFDVCxJQUFNLGdCQUFnQixHQUFHLGNBQWMsQ0FBQyxJQUFJLENBQUMsQ0FBQztRQUM5QyxJQUFJLElBQUksQ0FBQyxXQUFXLElBQUksZ0JBQWdCLEVBQUU7WUFDeEMsMEJBQU0sQ0FDRixDQUFDLElBQUksQ0FBQyxXQUFXLElBQUksQ0FBQyxnQkFBZ0IsRUFDdEMsaUNBQWlDLENBQUMsQ0FBQztZQUN2QyxJQUFNLFdBQVcsR0FBRyxJQUFJLENBQUMsV0FBVyxJQUFJLGdCQUFnQixDQUFDO1lBQ3pELElBQUksV0FBWSxDQUNYLElBQTZCLEVBQUUsT0FBZ0MsQ0FBQyxFQUFFO2dCQUNyRSxPQUFPLElBQUksQ0FBQzthQUNiO1NBQ0Y7UUFDRCx1RUFBdUU7UUFDdkUsY0FBYztRQUNkLElBQUksd0JBQWUsQ0FBQyxJQUFJLENBQUMsRUFBRTtZQUN6QixPQUFPLElBQUksQ0FBQztTQUNiO1FBQ0QsSUFBSSxDQUFDLElBQUksR0FBRyxPQUFPLENBQUMsSUFBSSxDQUFDO1FBQ3pCLE9BQU8sS0FBSyxDQUFDO0lBQ2YsQ0FBQztJQUVELG1DQUFjLEdBQWQsVUFBZSxXQUE0RDtRQUN6RSwwQkFBTSxDQUFDLENBQUMsSUFBSSxDQUFDLFdBQVcsRUFBRSxtQ0FBbUMsQ0FBQyxDQUFDO1FBQy9ELElBQUksQ0FBQyxXQUFXLEdBQUcsV0FBVyxDQUFDO0lBQ2pDLENBQUM7SUFTSCxpQkFBQztBQUFELENBQUMsQUFySEQsSUFxSEM7QUFySHFCLGdDQUFVIiwic291cmNlc0NvbnRlbnQiOlsiLyoqXG4gKiBAZmlsZW92ZXJ2aWV3XG4gKiBDb250YWlucyB0eXBlcyBhbmQgb2JqZWN0cyBuZWNlc3NhcnkgZm9yIFNveS1JZG9tIHJ1bnRpbWUuXG4gKi9cblxuaW1wb3J0ICcuL3NraXBoYW5kbGVyJztcblxuaW1wb3J0IHthc3NlcnR9IGZyb20gJ2dvb2c6Z29vZy5hc3NlcnRzJzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvYXNzZXJ0c1xuaW1wb3J0IHtJakRhdGF9IGZyb20gJ2dvb2c6Z29vZy5zb3knOyAgICAgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295XG5pbXBvcnQgU2FuaXRpemVkQ29udGVudEtpbmQgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZENvbnRlbnRLaW5kJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IHtJZG9tRnVuY3Rpb25NZW1iZXJzfSBmcm9tICdnb29nOnNveWRhdGEnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveV91c2Vnb29nX2pzXG5pbXBvcnQgKiBhcyBpbmNyZW1lbnRhbGRvbSBmcm9tICdpbmNyZW1lbnRhbGRvbSc7ICAvLyBmcm9tIC8vdGhpcmRfcGFydHkvamF2YXNjcmlwdC9pbmNyZW1lbnRhbF9kb206aW5jcmVtZW50YWxkb21cblxuaW1wb3J0IHtJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBwYXRjaE91dGVyLCBTS0lQX1RPS0VOfSBmcm9tICcuL2FwaV9pZG9tJztcbmltcG9ydCB7aXNUYWdnZWRGb3JTa2lwfSBmcm9tICcuL2dsb2JhbCc7XG5cbi8qKiBGdW5jdGlvbiB0aGF0IGV4ZWN1dGVzIElkb20gaW5zdHJ1Y3Rpb25zICovXG5leHBvcnQgdHlwZSBQYXRjaEZ1bmN0aW9uID0gKGE/OiB1bmtub3duKSA9PiB2b2lkO1xuXG4vKiogRnVuY3Rpb24gdGhhdCBleGVjdXRlcyBiZWZvcmUgYSBwYXRjaCBhbmQgZGV0ZXJtaW5lcyB3aGV0aGVyIHRvIHByb2NlZWQuICovXG5leHBvcnQgdHlwZSBTa2lwSGFuZGxlciA9IDxUPihwcmV2OiBULCBuZXh0OiBUKSA9PiBib29sZWFuO1xuXG4vKiogIEdldHRlciBmb3Igc2tpcCBoYW5kbGVyICovXG5mdW5jdGlvbiBnZXRTa2lwSGFuZGxlcihlbDogSFRNTEVsZW1lbnQpIHtcbiAgcmV0dXJuIGVsLl9fc295X3NraXBfaGFuZGxlcjtcbn1cblxuXG4vKiogQmFzZSBjbGFzcyBmb3IgYSBTb3kgZWxlbWVudC4gKi9cbmV4cG9ydCBhYnN0cmFjdCBjbGFzcyBTb3lFbGVtZW50PFREYXRhIGV4dGVuZHMge318bnVsbCwgVEludGVyZmFjZSBleHRlbmRzIHt9PiB7XG4gIC8vIE5vZGUgaW4gd2hpY2ggdGhpcyBvYmplY3QgaXMgc3Rhc2hlZC5cbiAgcHJpdmF0ZSBub2RlOiBIVE1MRWxlbWVudHxudWxsID0gbnVsbDtcbiAgcHJpdmF0ZSBza2lwSGFuZGxlcjpcbiAgICAgICgocHJldjogVEludGVyZmFjZSwgbmV4dDogVEludGVyZmFjZSkgPT4gYm9vbGVhbil8bnVsbCA9IG51bGw7XG4gIHByaXZhdGUgc3luY1N0YXRlID0gdHJ1ZTtcbiAgLy8gTWFya2VyIHNvIHRoYXQgZnV0dXJlIGVsZW1lbnQgYWNjZXNzZXMgY2FuIGZpbmQgdGhpcyBTb3kgZWxlbWVudCBmcm9tIHRoZVxuICAvLyBET01cbiAga2V5OiBzdHJpbmcgPSAnJztcblxuICBjb25zdHJ1Y3Rvcihwcm90ZWN0ZWQgZGF0YTogVERhdGEsIHByb3RlY3RlZCBpakRhdGE/OiBJakRhdGEpIHt9XG5cbiAgLyoqXG4gICAqIFN0YXRlIHZhcmlhYmxlcyB0aGF0IGFyZSBkZXJpdmVkIGZyb20gcGFyYW1ldGVycyB3aWxsIGNvbnRpbnVlIHRvIGJlXG4gICAqIGRlcml2ZWQgdW50aWwgdGhpcyBtZXRob2QgaXMgY2FsbGVkLlxuICAgKi9cbiAgc2V0U3luY1N0YXRlKHN5bmNTdGF0ZTogYm9vbGVhbikge1xuICAgIHRoaXMuc3luY1N0YXRlID0gc3luY1N0YXRlO1xuICB9XG5cbiAgcHJvdGVjdGVkIHNob3VsZFN5bmNTdGF0ZSgpIHtcbiAgICByZXR1cm4gdGhpcy5zeW5jU3RhdGU7XG4gIH1cblxuICAvKipcbiAgICogUGF0Y2hlcyB0aGUgY3VycmVudCBkb20gbm9kZS5cbiAgICogQHBhcmFtIHJlbmRlcmVyIEFsbG93cyBpbmplY3RpbmcgYSBzdWJjbGFzcyBvZiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyXG4gICAqICAgICAgICAgICAgICAgICB0byBjdXN0b21pemUgdGhlIGJlaGF2aW9yIG9mIHBhdGNoZXMuXG4gICAqL1xuICByZW5kZXIocmVuZGVyZXIgPSBuZXcgSW5jcmVtZW50YWxEb21SZW5kZXJlcigpKSB7XG4gICAgYXNzZXJ0KHRoaXMubm9kZSk7XG4gICAgLy8gSXQgaXMgcG9zc2libGUgdGhhdCB0aGlzIFNveSBlbGVtZW50IGhhcyBhIHNraXAgaGFuZGxlciBvbiBpdC4gV2hlblxuICAgIC8vIHJlbmRlcigpIGlzIGNhbGxlZCwgaWdub3JlIHRoZSBza2lwIGhhbmRsZXIuXG4gICAgY29uc3Qgc2tpcEhhbmRsZXIgPSB0aGlzLnNraXBIYW5kbGVyO1xuICAgIHRoaXMuc2tpcEhhbmRsZXIgPSBudWxsO1xuICAgIHBhdGNoT3V0ZXIodGhpcy5ub2RlISwgKCkgPT4ge1xuICAgICAgLy8gSWYgdGhlcmUgYXJlIHBhcmFtZXRlcnMsIHRoZXkgbXVzdCBhbHJlYWR5IGJlIHNwZWNpZmllZC5cbiAgICAgIHRoaXMucmVuZGVySW50ZXJuYWwocmVuZGVyZXIsIHRoaXMuZGF0YSEpO1xuICAgIH0pO1xuICAgIHRoaXMuc2tpcEhhbmRsZXIgPSBza2lwSGFuZGxlcjtcbiAgfVxuXG4gIC8qKlxuICAgKiBSZXBsYWNlcyB0aGUgbmV4dCBvcGVuIGNhbGwgc3VjaCB0aGF0IGl0IGV4ZWN1dGVzIFNveSBlbGVtZW50IHJ1bnRpbWVcbiAgICogYW5kIHRoZW4gcmVwbGFjZXMgaXRzZWxmIHdpdGggdGhlIG9sZCB2YXJpYW50LiBUaGlzIHJlbGllcyBvbiBjb21waWxlXG4gICAqIHRpbWUgdmFsaWRhdGlvbiB0aGF0IHRoZSBTb3kgZWxlbWVudCBjb250YWlucyBhIHNpbmdsZSBvcGVuL2Nsb3NlIHRhZy5cbiAgICovXG4gIHF1ZXVlU295RWxlbWVudChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZGF0YTogVERhdGEpIHtcbiAgICBjb25zdCBvbGRPcGVuID0gcmVuZGVyZXIub3BlbjtcbiAgICByZW5kZXJlci5vcGVuID0gKG5hbWVPckN0b3I6IHN0cmluZywga2V5ID0gJycpOiBIVE1MRWxlbWVudHx2b2lkID0+IHtcbiAgICAgIGNvbnN0IGVsID0gaW5jcmVtZW50YWxkb20ub3BlbihuYW1lT3JDdG9yLCByZW5kZXJlci5nZXROZXdLZXkoa2V5KSk7XG4gICAgICByZW5kZXJlci5vcGVuID0gb2xkT3BlbjtcbiAgICAgIGNvbnN0IG1heWJlU2tpcCA9IHRoaXMuaGFuZGxlU295RWxlbWVudFJ1bnRpbWUoZWwsIGRhdGEpO1xuICAgICAgaWYgKCFtYXliZVNraXApIHtcbiAgICAgICAgcmVuZGVyZXIudmlzaXQoZWwpO1xuICAgICAgICByZXR1cm4gZWw7XG4gICAgICB9XG4gICAgICAvLyBUaGlzIHRva2VuIGlzIHBhc3NlZCB0byAuL2FwaV9pZG9tLm1heWJlU2tpcCB0byBpbmRpY2F0ZSBza2lwcGluZy5cbiAgICAgIHJldHVybiBTS0lQX1RPS0VOIGFzIEhUTUxFbGVtZW50O1xuICAgIH07XG4gIH1cblxuICAvKipcbiAgICogSGFuZGxlcyBzeW5jaHJvbml6YXRpb24gYmV0d2VlbiB0aGUgU295IGVsZW1lbnQgc3Rhc2hlZCBpbiB0aGUgRE9NIGFuZFxuICAgKiBuZXcgZGF0YSB0byBkZWNpZGUgaWYgc2tpcHBpbmcgc2hvdWxkIGhhcHBlbi4gSW52b2tlZCB3aGVuIHJlbmRlcmluZyB0aGVcbiAgICogb3BlbiBlbGVtZW50IG9mIGEgdGVtcGxhdGUuXG4gICAqL1xuICBwcm90ZWN0ZWQgaGFuZGxlU295RWxlbWVudFJ1bnRpbWUobm9kZTogSFRNTEVsZW1lbnR8dW5kZWZpbmVkLCBkYXRhOiBURGF0YSk6XG4gICAgICBib29sZWFuIHtcbiAgICAvKipcbiAgICAgKiBUaGlzIGlzIG51bGwgYmVjYXVzZSBpdCBpcyBwb3NzaWJsZSB0aGF0IG5vIERPTSBoYXMgYmVlbiBnZW5lcmF0ZWRcbiAgICAgKiBmb3IgdGhpcyBTb3kgZWxlbWVudFxuICAgICAqIChzZWUgaHR0cDovL2dvL3NveS9yZWZlcmVuY2UvdmVsb2cjdGhlLWxvZ29ubHktYXR0cmlidXRlKVxuICAgICAqL1xuICAgIGlmICghbm9kZSkge1xuICAgICAgcmV0dXJuIGZhbHNlO1xuICAgIH1cbiAgICB0aGlzLm5vZGUgPSBub2RlO1xuICAgIG5vZGUuX19zb3kgPSB0aGlzIGFzIHVua25vd24gYXMgU295RWxlbWVudDx7fSwge30+O1xuICAgIGNvbnN0IG5ld05vZGUgPSBuZXcgKFxuICAgICAgICB0aGlzLmNvbnN0cnVjdG9yIGFzXG4gICAgICAgIHtuZXcgKGE6IFREYXRhKTogU295RWxlbWVudDxURGF0YSwgVEludGVyZmFjZT59KShkYXRhKTtcblxuICAgIC8vIFVzZXJzIG1heSBjb25maWd1cmUgYSBza2lwIGhhbmRsZXIgdG8gYXZvaWQgcGF0Y2hpbmcgRE9NIGluIGNlcnRhaW5cbiAgICAvLyBjYXNlcy5cbiAgICBjb25zdCBtYXliZVNraXBIYW5kbGVyID0gZ2V0U2tpcEhhbmRsZXIobm9kZSk7XG4gICAgaWYgKHRoaXMuc2tpcEhhbmRsZXIgfHwgbWF5YmVTa2lwSGFuZGxlcikge1xuICAgICAgYXNzZXJ0KFxuICAgICAgICAgICF0aGlzLnNraXBIYW5kbGVyIHx8ICFtYXliZVNraXBIYW5kbGVyLFxuICAgICAgICAgICdEbyBub3Qgc2V0IHNraXAgaGFuZGxlcnMgdHdpY2UuJyk7XG4gICAgICBjb25zdCBza2lwSGFuZGxlciA9IHRoaXMuc2tpcEhhbmRsZXIgfHwgbWF5YmVTa2lwSGFuZGxlcjtcbiAgICAgIGlmIChza2lwSGFuZGxlciFcbiAgICAgICAgICAodGhpcyBhcyB1bmtub3duIGFzIFRJbnRlcmZhY2UsIG5ld05vZGUgYXMgdW5rbm93biBhcyBUSW50ZXJmYWNlKSkge1xuICAgICAgICByZXR1cm4gdHJ1ZTtcbiAgICAgIH1cbiAgICB9XG4gICAgLy8gRm9yIHNlcnZlci1zaWRlIHJlaHlkcmF0aW9uLCBpdCBpcyBvbmx5IG5lY2Vzc2FyeSB0byBleGVjdXRlIGlkb20gdG9cbiAgICAvLyB0aGlzIHBvaW50LlxuICAgIGlmIChpc1RhZ2dlZEZvclNraXAobm9kZSkpIHtcbiAgICAgIHJldHVybiB0cnVlO1xuICAgIH1cbiAgICB0aGlzLmRhdGEgPSBuZXdOb2RlLmRhdGE7XG4gICAgcmV0dXJuIGZhbHNlO1xuICB9XG5cbiAgc2V0U2tpcEhhbmRsZXIoc2tpcEhhbmRsZXI6IChwcmV2OiBUSW50ZXJmYWNlLCBuZXh0OiBUSW50ZXJmYWNlKSA9PiBib29sZWFuKSB7XG4gICAgYXNzZXJ0KCF0aGlzLnNraXBIYW5kbGVyLCAnT25seSBvbmUgc2tpcCBoYW5kbGVyIGlzIGFsbG93ZWQuJyk7XG4gICAgdGhpcy5za2lwSGFuZGxlciA9IHNraXBIYW5kbGVyO1xuICB9XG5cbiAgLyoqXG4gICAqIE1ha2VzIGlkb20gcGF0Y2ggY2FsbHMsIGluc2lkZSBvZiBhIHBhdGNoIGNvbnRleHQuXG4gICAqIFRoaXMgcmV0dXJucyB0cnVlIGlmIHRoZSBza2lwIGhhbmRsZXIgcnVucyAoYWZ0ZXIgaW5pdGlhbCByZW5kZXIpIGFuZFxuICAgKiByZXR1cm5zIHRydWUuXG4gICAqL1xuICBhYnN0cmFjdCByZW5kZXJJbnRlcm5hbChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZGF0YTogVERhdGEpOlxuICAgICAgYm9vbGVhbjtcbn1cblxuLyoqXG4gKiBUeXBlIGZvciB0cmFuc2Zvcm1pbmcgaWRvbSBmdW5jdGlvbnMgaW50byBmdW5jdGlvbnMgdGhhdCBjYW4gYmUgY29lcmNlZFxuICogdG8gc3RyaW5ncy5cbiAqL1xuZXhwb3J0IGludGVyZmFjZSBJZG9tRnVuY3Rpb24gZXh0ZW5kcyBJZG9tRnVuY3Rpb25NZW1iZXJzIHtcbiAgKGlkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIpOiB2b2lkO1xuICBjb250ZW50S2luZDogU2FuaXRpemVkQ29udGVudEtpbmQ7XG4gIHRvU3RyaW5nOiAocmVuZGVyZXI/OiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyKSA9PiBzdHJpbmc7XG4gIHRvQm9vbGVhbjogKCkgPT4gYm9vbGVhbjtcbn1cbiJdfQ==
