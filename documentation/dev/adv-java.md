# Advanced Java Rendering

The `SoySauce` backend for Soy contains some advanced rendering features to help
with server stability and performance. This page describes how to use these
features.

[TOC]

## Rendering goals

When rendering on servers there are a number of important things to consider
that don't apply when rendering in JavaScript.

1.  Documents tend to be larger. Rather than rendering single components Soy is
    often rendering entire pages
1.  Documents should be streamed. Most server side renders produce an HTML
    document over HTTP. HTTP supports [Chunked Transfer
    Encoding](https://en.wikipedia.org/wiki/Chunked_transfer_encoding) which
    allows servers to stream documents; most Web Browsers can take advantage of
    this to display partial documents to users (or fetch referenced resources in
    parallel with document download).
1.  Servers often work with data from multiple backends. So the parameters
    passed to Soy templates may themselves require network operations.
1.  Servers are often communicating with slower clients or slow networks.

In order to work well in this complex environment, the `SoySauce` backend has
the following goals:

1.  Stream content from templates as rendering proceeds
1.  Detect when the output buffer is too full and 'stop' rendering
1.  Detect when rendering requires an unfinished `Future` object and 'stop'
    rendering
1.  Always attempt to make as much progress as possible before reaching ones of
    these 'stop' conditions

In order to achieve all these goals the `SoySauce` backend has implemented a
number of unique runtime behaviors. Some of these influence the rendering APIs
and others may change behavior in small ways.

### Lazy evaluation {#lazy}

In `SoySauce`[^1] all [let](../reference/let.md) and
[param](../reference/calls.md#param) commands are evaluated lazily.
Specifically, when Soy encounters a `let` instead of calculating the value of
the variable immediately[^2] it will generate a
[closure](https://en.wikipedia.org/wiki/Closure_\(computer_programming\)) and
only evaluate it when it is ultimately needed.

This aids performance in a number of ways:

1.  Large or complex values are calculated only right before use. So these
    values spend less time sitting in buffers
2.  If the let or param has an asynchronous data dependency then we can delay
    accessing which might allow the renderer to make more progress than it could
    otherwise

### Streaming escape directives {#streaming}

Soy supports [contextual autoescaping](../concepts/auto-escaping.md). This works
by inserting calls to special escaping functions around dynamic content. For
example, when you write

```soy
<div>{$foo}</div>
```

The compiler rewrites it as

```soy
<div>{$foo |escapeHtml}</div>
```

to ensure that the dynamic value `$foo` doesn't inject code. However, `$foo`
might be some really large value, or it might be a [lazy](#lazy) value in which
case rendering it might require waiting for some asynchronous data. To try to
make the maximum amount of progress rendering content Soy has implemented
_streaming_ versions of the most common escaping functions. This reduces the
amount of data that Soy needs to buffer and ensures that the maximum amount of
progress is made before we need to wait for any asynchronous data.

### Asynchronous rendering {#async}


The `SoySauce` backend has built in support for responding to asynchronous IO.
There are two kinds of asynchronous events that we handle:

1.  Users can pass unfinished `java.util.concurrent.Future` objects as template
    parameters. By doing this users can start rendering before all the required
    data is available. Due to [streaming](#streaming), page headers can be
    rendered and flushed to users early which may allow browsers to start
    displaying a partial page or downloading other resources[^3].
1.  The Soy renderer may write faster than the client can read the data. Soy
    should avoid blocking on `write` operations or buffering arbitrary amounts
    of data in memory. This is signaled by the
    `AdvisingAppendable.softLimitedReached` method on the output stream. Users
    who want to trigger this behavior will need to supply a custom
    `AdvisingAppendable` implementation and implement this method.
    *   **NOTE:** This mechanism is _cooperative_ and as such it depends on how
        often the renderer calls the `softLimitReached()` method. Currently, the
        renderer will call this method at the beginning of any `{template}` that
        isn't a simple delegate.

To handle these events the renderer will detect when it is about to evaluate an
unfinished future or when the output buffer is full and then pause rendering to
return control to the caller.


To see how this works, consider this example:

```soy
{namespace ns}

{template .foo}
  {@param userName: string}
  <div>
    {$userName}
  </div>
{/template}
```

```java
SoySauce soy = ...;
WriteContinuation continuation =
    soy.render("ns.foo")
        .setData(ImmutableMap.of("userName", fetchUserNameFuture()))
        .render(outputStream);
switch (continuation.result().type()) {
  case DONE:
    // do nothing, rendering completed
    break;
  case DETACH:
    // this means rendering stopped because we found a future
    continueAfter(continuation.result().future(), () -> continuation.continueRender());
    break;
  case LIMITED:
    // output buffer is full.  See below
    continueAfter(outputStream.isReady(), () -> continuation.continueRender());
    break;
}
```

In this example, we render the above template by passing it a future for the
user name and having it render to an output stream. The `render` method will
return a `WriteContinuation` object that can signal the status of the rendering
operation. There are 3 different options:

1.  `DONE` this means that rendering is complete
1.  `DETACH` this means that rendering paused due to an unfinished `Future`. The
    particular future is available via the `RenderResult.future()` method.
1.  `LIMITED` this means that the output stream told us to stop rendering

How to handle each event depends stronly on the particular environment of the
rendering operation. For example,

*   If the future is a `ListenableFuture` then a listener could be attached and
    rendering could continue in the callback.

*   If the HTTP server you are using supports asynchronous request processing
    (like the Servlet 3.0 `AsyncContext`), then you could integrate with that to
    continue your rendering after the future is complete.


<br>

[^1]: `Tofu` also supports lazy `let` and `param` commands, with largely the
    same behavior.
[^2]: For some 'trivial' `let` or `param` declarations we do calculate them
    eagerly, but only when we know it is cheap and doesn't have complex data
    dependencies.
[^3]: In `Tofu`, the behavior is trivial, when the renderer comes across an
    unfinished future, it flushes the output appendable (if it is `Flushable`)
    and then blocks waiting on the future.
