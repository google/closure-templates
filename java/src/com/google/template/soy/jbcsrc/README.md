[TOC]

# A Bytecode Compiler for Soy

This package implements a bytecode compiler for the Soy language. The high level
goals are to

*   Increase rendering performance over Tofu
*   Allow async rendering so that Soy can pause/resume rendering when
    *   It encounters an unfinished future
    *   The output buffer is full

The general strategy is to generate a new Java class for each Soy `{template}`.
Full details on how different pieces of Soy syntax map to Java code are detailed
below.

For information on how to develop the compiler see [the development guide](development-guide.md).


## Background

The Soy server side renderer is currently implemented as a [recursive
visitor](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/sharedpasses/render/RenderVisitor.java)
on the Soy AST. This implementation is expedient since the renderer uses the
same API as all the parse visitors and other 'compiler passes' and thus can
benefit from developer familiarity. However, this design makes it very difficult
to perform even basic optimizations. By contrast, the JS implementation of Soy
works by generating JS code and thus can benefit from all the optimizations in
the JS compiler and browser. The new Python implementation will work in a
similar way, by generating Python code.

Finally, Soy rendering is one of the last sources of blocking IO in modern Java
servers. Soy will block the request thread when coming across unfinished futures
or when the output buffer becomes full. The current design of Soy rendering
makes it very difficult to move to a fully asynchronous rendering model. This is
important for production stability and resource utilization since it is much
easier to provision servers when the number of threads needed to serve incoming
requests doesn't depend on worst-case backend latency.

## Overview

For each Soy template we will generate a Java class by generating bytecode
directly from the parse tree. The Soy language is simple and all the basic
language constructs map directly into Java constructs. For example, this
template:

~~~soy
{template .foo}
  {@param p : string}
  {@param p2 : string}
  {$p}
  {$p2}
{/template}
~~~

could be implemented by a Java function like:

~~~java
  void render(Appendable output, SoyRecord params) {
    params.getField("p").render(output);
    params.getField("p2").render(output);
  }
~~~

Which is, in fact, effectively the code that the current implementation executes
(it is just that between each method call there are many many visitor
operations). So at least initially most of the benefit would be realized simply
by not traversing the AST. However, this solution still doesn’t address our
concerns about asynchrony.

There are two kinds of asynchrony we will wish to handle:

1.  Asynchronous data. Any piece of data passed into Soy is wrapped in a
    `SoyValueProvider`. If the item is a
    [`Future`](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html),
    it is wrapped in a `SoyFutureValueProvider`. If `$p` above was passed in as
    a `Future`, then we would block during the `getField` method call.
2.  Asynchronous output. In a modern HTTP server, it is desirable to handle slow
    clients (e.g. mobile devices). However, in the current Soy design if the
    server writes too fast it will either block the rendering thread (causing
    poor thread utilization) or it will buffer unbounded bytes in RAM. If we are
    buffering too much, it may be better for rendering to pause and for the
    request thread to serve another request while waiting for output buffers to
    drain.

Given these constraints, the above direct approach will not work. So instead we
could generate something like this:

~~~java
class Foo {
  int state = 0;
  StringData p;
  StringData p2;

  Result render(AdvisingAppendable output, SoyRecord params) {
    switch (state) {
      case 0:
        SoyValueProvider provider = params.getFieldProvider("p");
        Result r = provider.status();
        if (r.type() != Result.Type.DONE) {
          return r;
        }
        p = (StringData) provider.resolve();
        p.render(output);
        state = 1;
        if (output.softLimitReached()) {
          return Result.limited();
        }
      case 1:
        provider = params.getFieldProvider("p2");
        Result r = provider.status();
        if (r.type() != Result.Type.DONE) {
          return r;
        }
        p2 = (StringData) provider.resolve();
        p2.render(output);
        state = 2;
        if (output.softLimitReached()) {
          return Result.limited();
        }
      case 2:
        return Result.done();
      default:
        throw new AssertionError();
    }
  }
}
~~~

In this example, we are now checking whether the output is full (after every
write operation) and we are checking if the `SoyValueProviders` can be
'resolved' without blocking prior to resolving. Additionally, we are storing
resolved parameters in fields so that we don’t have to re-resolve them when
re-entering the method.

This is the heart of the design: to generate for each template a tiny state
machine that can be used to save and restore state up to the point of the last
'detach'. A sophisticated rendering client could then use these return types to
detach from the request thread or find other work to do while buffers are being
flushed or futures are completing.

This approach is similar to how `yield` generators are implemented in C#/Python
or how `async/await` are implemented in C#/Scala/Dart.

### Implementation strategy

All the examples below use Java code to demonstrate what the generated code will
look like. However, the actual implementation will be using
[ASM](http://asm.ow2.org/) (a bytecode manipulation library) to generate
bytecode directly. This comes with a number of pros and cons.

*   Pros
    *   Small library. Fast code generation.
    *   Greater control flow flexibility (bytecode GOTO is more powerful than a
        Java switch statement).
    *   Can generate debugging information that points directly to the Soy
        template resources.
    *   Makes refresh-to-reload more straightforward than a source compiler
        based approach would be.
*   Cons
    *   Few people are familiar with bytecode. This may be a high barrier to
        entry for contributions.
    *   Verbose/tedious! (we lose all the javac compiler magic that you normally
        get)
    *   New compile time dependency for Soy (ASM library).

To demonstrate the control flow issues mentioned above, consider the following
example:

~~~soy
{template .foo}
  {@param p1 : [f: bool, v: list<string>]}
  {if $p1.f}
    {for $s in $p1.v}
      <div>{$s}</div>
    {/for}
  {/if}
{/template}
~~~

This is a simple template with a `for` loop inside an `if` statement.

To allow the renderer to suspend rendering after print statements or to
implement detaching when handling `$s` we would need to implement something like
this:

~~~java
int index;
int state;

public Result render(SoyRecord params, Appendable output) throws IOException {
  while (true) {
    switch (state) {
      case 0:
        SoyRecord soyRecord = (SoyRecord) params.getField("p1");
        if (soyRecord.getField("f").coerceToBoolean()) {
          state = 1;
        } else {
          state = 3;
        }
        break;
      case 1:
        SoyListData vList =
            ((SoyListData) ((SoyRecord) params.getField("p1")).getField("v"));
        if (vList.length() > index) {
          output.append("<div>");
          state = 2;
        } else {
          state = 3;
        }
        break;
      case 2:
        SoyValueProvider s =
            ((SoyListData) ((SoyRecord) params.getField("p1")).getField("v"))
            .asJavaList()
            .get(index);
        Result resolvable = s.status();
        if (resolvable.isDone()) {
          s.resolve().render(output);
          output.append("</div>");
          state = 1;
          index++;
        } else {
          return resolvable;
        }
        break;
      case 3:
        return Result.done();
    }
  }
}
~~~

We could generate code like this, but in doing so we would lose the major
benefits of source generation: human readability and debuggability. So given
that, we have decided not to generate Java sources and instead to generate
bytecode directly. For example, if Java had a `goto` keyword we could rewrite
the above as:

~~~java
int index;
int state = 0;
public void render(SoyRecord params, Appendable output) throws IOException {
  goto state;
  L0:
  SoyRecord soyRecord = (SoyRecord) params.getField("p1");
  if (soyRecord.getField("f").coerceToBoolean()) {
    List<? extends SoyValueProvider> asJavaList =
        ((SoyListData) soyRecord.getField("v")).asJavaList();
    for (index = 0; index < asJavaList.size(); index++) {
      output.append("<div>");
      L1:
      SoyValueProvider s = asJavaList.get(index);
      Result r = s.status();
      if (!s.isDone()) {
        state = 1;
      }
      s.resolve().render(output);
      output.append("</div>");
    }
  }
}
~~~

The strategy is to generate bytecode that looks like that.

# Structure of Compiled Templates

For every Soy template we compile a number of classes to implement our
functionality:

*   A
    [`CompiledTemplate`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/shared/CompiledTemplate.java)
    subclass. This has a single `render` method that will render the template.
*   A
    [`CompiledTemplate.Factory`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/shared/CompiledTemplate.java)
    subclass. This provides a non-reflective mechanism for constructing
    `CompiledTemplate` instances.
*   A
    [`DetachableSoyValueProvider`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/runtime/DetachableSoyValueProvider.java)
    subclass for each
    [`CallParamValueNode`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/soytree/CallParamValueNode.java)
    and each
    [`LetValueNode`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/soytree/LetValueNode.java).
    These allow us to implement 'lazy' `{let ...}` and `{param ...}` statements.
*   A
    [`DetachableContentProvider`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/runtime/DetachableContentProvider.java)
    subclass for each
    [`CallParamContentNode`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/soytree/CallParamContentNode.java)
    and each
    [`LetContentNode`](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/soytree/LetContentNode.java).
    These allow us to implement 'lazy' `{let ...}` and `{param ...}` statements
    that render content blocks.

### Glossary

A few specialized terms are used throughout this document and the
implementation.

*   `detach`: A `detach` is the act of pausing rendering, saving execution state
    and returning control to our caller. We are 'detaching' the current
    rendering thread from the render operation.
*   `attach`: The counterpart of `detach`. This is the act of attaching a new
    thread to a detached rendering operation. We may also use the term
    `reattach`.

### Helper objects and APIs

Our implementation will depend on a few new helper objects.

#### `AdvisingAppendable`

A simple `Appendable` subtype that exposes an additional method `boolean
softLimitReached()`. This method can be queried to see if writes should be
suspended.

#### `RenderResult`

A value type that indicates the result of a rendering operation. The 3 kinds
are:

*   `RenderResult.done()`: rendering completed fully
*   `RenderResult.limited()`: the output informed us that the limit was reached
*   `RenderResult.detach(Future)`: rendering found an incomplete future and is
    detaching on that

#### `RenderContext`

A somewhat catch-all object for propagating cross-cutting data items. Via the
`RenderContext` object, templates should be able to access:

*   The `SoyMessageBundle`
*   `SoyFunction` instances
*   `PrintDirective` instances
*   renaming maps (css, xid)
*   `EscapingDirective` instances
*   `$ij` params
*   `DeltemplateSelector`

We will propagate this as a single object from the top level (directly through
the `render()` calls), because this object will be constant per render.

As future work we should turn many of these into compiler plugins. For example,
instead of looking up the `PrintDirective` instances each time we need to apply
it, we could instead introduce a `SoyJbcsrcPrintDirective` that would run in the
compiler and then we wouldn't need to look up instances at runtime. This would
be similar to how `jssrc` implements `SoyFunction`s and `SoyPrintDirective`s.

Additionally we will enhance some core APIs to expose additional information:

#### `SoyValue`

`void SoyValue.render(Appendable)` will change to `RenderResult
render(AdvisingAppendable)`. That will allow individual values to detach
mid-render. Most Soy values will have trivial implementations of this method,
but for our [lazy transclusion
values](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/runtime/DetachableContentProvider.java)
we will need this.

#### `SoyValueProvider`

`SoyValueProvider`s are used to encapsulate values that aren’t done yet. This
includes lazy expressions as well as `Future`s. In order to identify and
conditionally resolve these providers we will need a new `RenderResult status()`
method.

## Compilation strategy

The main details of the design will be a discussion of exactly how the code of
the render method is generated. The Soy language is logically divided into two
parts: the expression language and the command language. The expression language
is everything inside of a set of `{}`s, while the command language is everything
outside of it. Since the expression language is the simplest part, we will start
there.

### Compiling Soy expressions

Soy has a relatively simple expression language divided into 4 main parts:

1.  Literals: `1`, `'foo'`, `[1,2,3,4]`, `['k': 'v', 'k2': 'v2']`
2.  Operators: `+`, `-`, `==`, `?:` etc.
3.  Function invocations: `index()`, `isFirst()`, etc.
4.  Data access expressions: `$foo`, `$foo.bar.baz`, `$foo[$key]`, `$foo[1]`

Since expressions are (for the most part) where data access occurs, it is in the
expressions that we must handle resolving `SoyValueProviders` to `SoyValues` and
optionally detaching if we come across a future. One simplifying assumption we
will make is that Soy expressions are idempotent and sufficiently cheap
(relative to a detaching operation) that it is fine to re-execute an expression
when re-attaching.

#### Operators, literals and function invocations

These 3 parts of the expression language translate quite directly and do not
interact with either the output stream or any data that may contain futures, and
therefore do not have any complex control flow requirements.

The biggest optimization opportunities exist in this part of the implementation.
Soy tracks a fair bit of type information in order to flag issues at parse time
as well as to generate type casts in the JS implementation. However, the Java
runtime hasn’t been able to take advantage of any benefits from specialization
due these types. For example, the expression `$a + $b` has somewhat complex
semantics since Soy has essentially the JavaScript rules for the `+` operator.
So in order to execute the operator we need to know if either of the parameters
is a string or a number and then decide to concat or sum. The current Tofu
implementation is
[here](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/sharedpasses/render/EvalVisitor.java)
and implements this by a sequence of explicit type checks at runtime. This is
unfortunate since in a large number of cases the types are fixed at parse time.
Because we will generate code for each `+` operator we can specialize the
implementation based on the types of the subexpressions and move many of these
type checks from runtime to compile time.

Finally, another obvious optimization in expression evaluation is the removal of
`SoyValue` boxes. If an expression is fully typed, then we could eliminate all
the `SoyValue` wrappers and instead operate directly on raw `long`s, `double`s
and `String`s.

For future work we should consider using the java7 `invokedynamic` instruction
to optimize this further. This would allow us to specialize based on runtime
types.

#### Data access

When coming across a data reference we will need to generate code to
conditionally resolve it. Resolution may mean one of several things.

There are two kinds of data access:

*   VarRef
    *   For each of these we will generate a field to hold the
        `SoyValueProvider`.
    *   To access, we first check if the provider has been resolved, if it
        hasn’t been we then resolve the variable.
    *   If the provider is resolvable (via the `status()` method), then we
        `resolve()`.
    *   If is isn’t resolvable, we calculate a `RenderResult` object, store our
        state and return.
    *   For future work we can use a version of definite assignment analysis to
        eliminate some checks. For example, if it is definitely not the first
        access, then we can just read the field, no need to generate any code
        beyond that. An initial version of this is in `TemplateAnalysis`.
*   DataAccess
    *   These are for accessing subfields, map entries or list items.
    *   There are no fields to check so we grab the item as a provider, check
        `status()` and conditionally detach.

For example, a VarRef `$foo` referring to a template param may be implemented
as:

~~~java
SoyValue fooValue = fooField;
if (fooValue == null) {
  // first access
case N:
  SoyValueProvider valueProvider = params.getFieldProvider("foo");
  RenderResult r = valueProvider.status();
  if (r.type() != Type.DONE) {
    return r;
  }
  fooValue = fooField = valueProvider.resolve();
}
state = N + 1;
~~~

Obvious optimizations of this code may include:

*   eliminating the field if the var is only accessed once
*   not checking for `null` (or generating a new state) if this is provably not
    the first reference in the template

DataAccess nodes will be similar with the caveat that they will be referencing
subfields of other `SoyValue`s instead of from the params.

## Compiling Soy commands

Soy commands are the most complex part of the design. They may contain complex
control flow or define complex objects. The next section will go through all the
Soy commands and discuss exactly how they would be implemented.

### RAW\_TEXT\_NODE

Trivial compilation. Simply translates to:

~~~java
case N:
output.append(RAW_TEXT);
state = N + 1;
if (output.isSoftLimitReached()) {
  return RenderResult.limited();
}
~~~

So for each RAW\_TEXT command we will need to allocate a state and check the
output for being limited after writing.

Issues:

*   The text constant may be very large. We may want to rewrite as multiple
    write operations if the constant is very large (>1K? >4K?)
*   The jvm limits string constants to <64K bytes (in modified UTF8), so for
    very large content blocks we have to split into multiple writes.
*   For small writes we should attempt to eliminate soft limit checks if we have
    only written a few characters. Coming up with reasonable heuristics here
    will be the hard part (e.g. <100 chars? bytes?)

### PRINT\_NODE, PRINT\_DIRECTIVE\_NODE

The general form of a print command is

~~~soy
{print <expr>|<directive1>|<directive2>...}
~~~

(Note that the `print` command name is optional and often omitted)

To evaluate this statement we will first use the expression compiler to generate
code that produces a SoyValue object, then we will invoke code that looks like
this:

~~~java
case N:
expr = …;
expr = context.getPrintDirective("directive1").apply(expr);
expr = context.getPrintDirective("directive2").apply(expr);
state = N + 1;

case N+1:
Result r = expr.render(output);
if (r.type() != Type.DONE) {
  return r;
}
state = N + 2;
~~~

### XID\_NODE, CSS\_NODE

These nodes are truly trivial. In fact it was probably a mistake to implement
them as commands instead of just a `SoyFunction`.

In Tofu we currently use a single-element cache optimize renaming. See
[CssNode.renameCache](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/soytree/CssNode.java&l=83)
. This is one of the few examples of an optimization that would be lost in the
redesign. Based on profiling of SoySauce applications, renaming does not appear
to be on the hot path, but if we thought it was important we could optimize this
(via the same technique, or possibly by using integer keys and array lookups
instead of hash lookups, which may be simpler/smaller/faster).

### LET\_VALUE\_NODE, LET\_CONTENT\_NODE

`{let ..}` statements are more complex than you might think! Due to our desire
for laziness we cannot simply evaluate and stash in a field. Instead we generate
a class for each `{let}` command. For let value nodes, we will generate a
`DetachableSoyValueProvider` subclass, for `SoyContentNodes` we will generate a
`DetachableContentProvider` subclass. For example, assume that the template
`ns.owner` declares this let variable `{let $foo : $a + 1 /}`, will generate the
following code:

~~~java
private static final class let$foo_1 extends DetachableSoyValueProvider {
  private final ns$$owner owner;
  private int state;
  let$foo_1(ns$$owner owner) {
    this.owner = owner;
  }
  @Override protected Result doResolve() {
     // evaluate expression using normal rules
     // finally take the resolved expression and
     // assign to the value field (defined by our
     // super class)
     this.value = expr;
     return Result.done();
   }
}
~~~

Then the owner class will declare a field of type let$$foo\_1 and initialize it
at the normal declaration point. Let-content nodes will be very similar with the
caveat that the base class will be different (DetachableContentProvider). Unlike
params, the fields for let nodes need to be cleared (nulled out), when they go
out of scope. This is to sure that they behave properly in loops (re-evaluated
per iteration) and it will also make sure we don’t pin their values in memory
too long.

Optimizations performed on lets:

*   Identify constant lets eagerly evaluate the expression to avoid generating
    the closure.

*   Identify lets/params that simply alias other lets/params and 'inline' the
    references. e.g. `{let $foo : $bar /}` doesn't need a subclass.

*   TODO: identify lets that (based on control flow analysis) will not need
    detach logic and eagerly evaluate. (Work for this has started in
    `TemplateAnalysis`)

### IF\_NODE, IF\_COND\_NODE, IF\_ELSE\_NODE

If conditions will translate quite naturally since the Soy semantics and the
java semantics are identical.

### SWITCH\_NODE, SWITCH\_CASE\_NODE, SWITCH\_DEFAULT\_NODE

The behavior of switch is fairly similar to a sequence of if and else-if
statements (and will be implemented just like that), however, because each
comparison references the same SoyValue and we could detach mid-comparison. We
need to store the switch expression in a field.

Note: this analysis is based on the assumption that switch case statements may
be arbitrary expressions. The AST and current implementation imply that they
are.

TODO(lukes): change Soy semantics to ensure that switch case expressions are
constants, then the implementation could resolve to something like a Java
`switch()` statement, which would be preferable.

### FOREACH\_NODE, FOREACH\_NONEMPTY\_NODE, FOREACH\_IFEMPTY\_NODE, FOR\_NODE

For loops are also pretty straightforward with 2 important caveats.

1.  The loop variable, the loop collection, and the current index all need to be
    stored as fields so that the loop state can be recovered when reattaching.
2.  The non-empty and if-empty blocks can be implemented via simple loop
    unrolling.

### LOG\_NODE

A `{log}...{/log}` statement is simply a collection of Soy statements that
should render to `System.out` instead of the user supplied output buffer. This
is implemented by simply generating code for all the child statements (as
normal), but replace references to `output` with a trivial adapter of
`System.out` to the AdvisingAppendable interface. Additionally, we can skip
generating any and all `softLimitChecks` since `System.out` doesn’t have an
appropriate implementation.

NOTE: this does mean that log statements can block the render thread while
waiting for stdout buffers to flush to disk. This is considered acceptable since
log statements are generally only used for debugging.

### DEBUGGER\_NODE

No op implementation. We can generate a label with a line number here, but that
is about it.

### CALL\_PARAM\_VALUE\_NODE, CALL\_PARAM\_CONTENT\_NODE

See the section on [`{let}` commands](#let_value_node_let_content_node),
`{param}` commands will use an identical strategy for defining the values. Each
one will be stored in a SoyRecord that will be passed as an argument to the next
template.

N.B. None of the `{param}` values or the SoyRecord holding them for a call will
be stored as fields, see the section on [template
calling](#call_basic_nodecall_delegate_node) for a detailed example.

### CALL\_BASIC\_NODE, CALL\_DELEGATE\_NODE

There are several styles of calls. For now I will demonstrate a normal call with
no data param. e.g.

`{call .foo}{param bar : 1 /}{/call}`

This will generate code that looks like:

~~~java
private ns$$foo fooTemplate;
  case N:
    SoyRecord record = new ParamStore();
    record.setField("bar", <generate bar param>);
    fooTemplate = new ns$$foo(record);
    state = N+1;
  case N+1:
    Result r = fooTemplate.render(output, context);
    if (r.type() != Type.DONE) {
      return r;
    }
~~~

parameters like `data = "all"` or `data="$expr"` will simply modify how the
record is initialized.

For `{delcall...}`s the process is mostly the same, but instead of invoking the
callee constructor directly, we instead trigger deltemplate selection by
invoking `RenderContext.getDelTemplate` which selects and constructs the target
callee.

Optimizations and future work:

*   We should eliminate the `SoyDict` parameter map whenever possible. Most
    calls pass a fixed set of params and in those cases we can eliminate
    allocations and map operations by just generating a specialized constructor
    in the callee.

### MSG\_NODE, MSG\_FALLBACK\_GROUP\_NODE

Soy has direct support for translations. In `jssrc`, this is mostly delegated to
`goog.getMessage`, but in SoySauce we don't have such a good option, instead we
handle rendering and placeholder substitution ourselves. `{msg ..}` rendering
breaks into 2 cases

*   Simple constant messages: This is for when there are no parameters, in these
    cases we can calculate the message id in the compiler and look it up
    directly in the `SoyMsgBundle`. Here we generate code that directly calls:
    `renderContext.getSoyMessge(<id>).getParts().get(0).getRawText()`.
*   Messages with placeholders (including gendered messages and plurals): For
    these the rendering strategy is much more complex since translators may move
    placeholders around, introduce new plurals cases, etc. So for this we use a
    runtime library to interpret the `SoyMsg` object against a map of
    placeholder objects. So the compiler mostly generates code to populate the
    placeholder map. See `Runtime.renderSoyMsgWithPlaceholders`

Future Optimizations:

*   For plurals and gendered messages we can generate more specialized calls to
    avoid boxing the plurals variable and having to pass the gender parameter in
    the placeholder map.

## Compiling Soy types

The Soy type system mostly follows the JS type system (as understood by the JS
compiler). Notably, it doesn’t really fit into the Java type system. The current
renderer manages this disconnect via the [SoyType type
hierarchy](https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/types/SoyType.java)
and a plethora of runtime type checks. Currently the runtime checks are a
combination of explicit `SoyType` operations and the Java <b>`instanceof`</b>
operator. This will cause a variety of problems for the compiler:

*   Soy has a number of places where it checks types. We will need to generate
    code that performs these checks by generating `checkcast` instructions.
*   Soy has union types. These are not (easily) representable in the Java type
    system, so every union typed variable will most likely by represented by a
    static `SoyValue` type.
*   Soy has both `null` values and a `null` type.
*   The Soy type system is pluggable (notably for protos).
*   The Soy type system is not completely accurate (e.g. nullability is not
    trustable).

Due to these issues we take a conservative approach to how we make use of the
type system. The key principles we will use are:

*   If the user declared it, we should enforce it (with `checkcast` operations).
    This way type errors will get caught early and often.
*   Nullability information from the type system cannot be relied on. For
    example, if `map` contains integer values, then `$map['key']` will be
    assigned the type `int` by the type system, but `int|null` is probably more
    accurate since we don't know whether or not the key exists. So in general we
    need to be careful when dealing with possibly null expressions. To deal with
    this we have our own concept of nullability (`Expression.isNullable()`).
*   Type information from the compiler is best effort only. The Soy type system
    was designed mostly for adding some compile time checks and generating
    accessors in the jssrc backend. Using it to generate code for Soy
    expressions is quite difficult. (This is really just a generalization of the
    above point.)

## Runtime dependencies

The generated `Soy` classes will need access to a number of runtime libraries to
perform basic logic. The most obvious ones are:

*   `com.google.template.soy.jbcsrc.runtime`: Contains `jbcsrc` specific runtime
    libraries.
*   `com.google.template.soy.jbcsrc.shared`: Defines common interfaces for
    normal Java code to interact with the generated code.
*   `com.google.template.soy.data`: Defines common data representations for
    passing data to/from Soy.
*   `com.google.template.soy.shared.internal.SharedRuntime`: Defines runtime
    libraries that are shared between jbcsrc and tofu.

There is a long tail of additional libraries that are needed that are scattered
across Soy packages. In the long run we should seek to consolidate this kind of
runtime support into a smaller set of packages and then eventually release a
separate maven artifact to encapsulate them. In this way servers can avoid
depending on the compiler at runtime.

## Non-functional requirements

Cross-cutting architectural issues that influence overall design choices.

*   Refresh to reload. Soy development mode should not change at all.
    *   This should be pretty straightforward with bytecode generation since it
        is just as hard to use it to generate a Jar vs. loading the classes into
        the current VM.
    *   For reloading we would just reparse and recompile into a different (heap
        sourced) class loader. There is some risk that we will leak permgen, so
        we should write leak tests for the classloaders.
*   Stack traces are readable! Currently the tofu renderer does a lot of work to
    generate stack traces that point to the templates. We should do the same.
*   Efficiency! This new system should be significantly faster (>20% cpu
    reduction) than the current approach.
*   Reasonable permgen usage. This will add a lot of new classes to the JVM
    which may consume too much permgen. In general, we are fine with trading
    server ram for cpu, but there are limits.

## Compatibility

There are a number of places where SoySauce has slightly different semantics
than Tofu. We have tried to minimize these as much as possible but in a few
cases we prefer the SoySauce semantics (generally because they demonstrate
errors or ambiguity in user templates). I will attempt to document all known
incompatibilities here:

*   SoySauce disallows (at compile time) calls to undefined templates. Tofu
    turns these into runtime failures. This may create build failures in
    otherwise dead code.
*   Stricter type checking of template parameters. Tofu does runtime type
    checking but it is somewhat limited in the accuracy of these checks. For
    example:

    *   If you declare that a template has a param `{@param foos :
        list<string>}` Tofu will assert that the value is actually a list.
        SoySauce will do that, but it will also assert that `$foos[0]` is a
        string by checking it on access (this is the same strategy that java
        uses for generics).

    *   Tofu fails to type check params which are statically typed to `?`, this
        is a known bug.
        SoySauce does not have this bug so user templates relying on it will
        have to be fixed.

*   SoySauce is stricter about dereferencing `null` objects. For example, given
    the expression `isNonnull($foo.bar.baz)` if `bar` is `null` then accessing
    `.baz` on it should cause an error, and it does in SoySauce and the JS
    backend, however, in Tofu this doesn’t happen (though there is a TODO),
    instead it only causes an error if you perform certain operations with the
    result of the expression (calling `isNonnull` and simple comparisons the
    only thing you can do). An appropriate fix would be to rewrite it as
    `isNonnull($foo.bar?.baz)`.

*   SoySauce interprets 'required' template parameters slightly differently than
    Tofu. Imagine this template:

    ```soy
    {template .foo}
      {@param p : string}
      {$p}
    {/template}
    ```

    In Tofu, if you call `.foo` without passing `$p` there are a few things that
    can happen:

    *   If it is a top level call (Java code calling `.foo`), then you will get
        a `SoyTofuException` saying that a required parameter is missing.
    *   If it is a Soy->Soy call then you will get `null` for `$p`

    In SoySauce you always get `null`. We chose this option because it is more
    internally consistent (soy->soy and java->soy calls are treated
    equivalently) and it is more consistent with the behavior of the JavaScript
    Soy backend.

