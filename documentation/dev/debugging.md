# Debugging


Soy has a few features to aid debugging of templates.

[TOC]

## Debugger

The [`{debugger}` command](../reference/debugger) is a simple tool to help debug
your templates.

*   In the JavaScript backends this turns into a
    [`debugger;`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/debugger)
    statement which will trigger a breakpoint in most browsers.
*   In the Python backend this turns into a
    [`pdb.set_trace()`](https://docs.python.org/2/library/pdb.html) which will
    trigger a breakpoint if a debugger is attached.
*   In the Java backends this does nothing besides generate a source reference
    in the Bytecode.

## Log

Sometimes good old `println` debugging is what you need. For this Soy offers the
[`{log}...{/log}` command](../reference/log)

*   In JavaScript, is implemented as
    [`console.log` statement](https://developer.mozilla.org/en-US/docs/Web/API/Console/log)
*   In Python, is implemented as
    [`print` statement](https://docs.python.org/3/library/functions.html#print)
*   In Java, is implemented as
    [`System.out.println`](https://docs.oracle.com/javase/7/docs/api/java/io/PrintStream.html#println\(\))

## Inspecting rendered documents

For large documents, it can be difficult to figure out which part of a page was
rendered by which templates. For this we offer a simple instrumentation of
templates where each 'root HTML tag' in a template block has an extra
`data-debug-soy` attribute that documents what template rendered it.

A 'root HTML tag' in a template block is defined as any HTML tag that doesn't
definitely have a parent tag in the same `template` `let` or `param` block. This
heuristic has a fairly simple implementation so there may be some cases where we
instrument some tags that do have parents, but we shouldn't under instrument.
The goal of the heuristic is to be able to identify a call-stack from the
rendered document.

There are also JavaScript libraries that can read these annotations to give a
simple debugging UI. See below for how to configure this.


### Enabling the instrumentation


Enabling this feature by yourself is also possible.

*   In server side, you need to tell the Soy renderer that you want to add
    additional HTML attributes for debug usages. Use the
    `SoySauce.Renderer.setDebugSoyTemplateInfo(boolean)` method API.


*   On the client you need to call `soy.setDebugSoyTemplateInfo(true)` to enable
    the instrumentation.

    NOTE: your JavaScript should also be compiled with `goog.DEBUG` set to
    `true`

## Known Issues

*   The debugging support works by annotating HTML tags with metadata about the
    template that rendered them. Therefore if you have content that isn't
    directly associated with an html node, then attribution might not work. For
    exapmle, if you have a template that only renders text (no DOM) then on
    hovering over the text it will show the closest caller template that does
    have a DOM node.
