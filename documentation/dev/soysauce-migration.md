# Migrating to SoySauce from Tofu


The SoySauce backend for server side rendered Soy has a number of benefits over
the Tofu interpreter

*   Advanced support for asynchronous IO (see [Advanced Java
    rendering](adv-java.md) for details)
*   It allocates less memory and consumes less CPU to render the same templates
    *   Benchmarks show speedups on the low end at 40% though the actual results
        you will see depend on exactly what your templates are doing
*   It compiles to Java bytecode instead of using a runtime interpreter, so your
    application will no longer need to compile a new Tofu during startup.
*   It supports new features like
    *   streaming escaping directives which reduce the number data copies and
        intermediate buffers
    *   [structured document logging](doc-logging.md) is not supported by Tofu
        and there are no plans to backport support
    *   ... and basically all future performance/feature development

For all these reasons the Tofu backend is discouraged for new users and all
users are encouraged to migrate.


[TOC]

## Migration Steps

## 1. Setup compilation

First you will need to compile your templates with the new compiler. This is
theoretically as simple as changing your calls to `SoyFileSet.compileToTofu()`
to call `SoyFileSet.compileTemplates()` instead.

This may reveal a few issues:

*   `compileTemplates()` requires that there are Java implementations of all the
    referenced plugins function/directives at compile time. (In Tofu this is
    only required at runtime.) Such calls are most likely in dead code so the
    templates should either be excluded from the compilation set, or the plugins
    should be modified to support Java rendering.

## 2. Setup precompilation

Next, you will want to get precompilation working. The Tofu backend interprets
the `.soy` source files at runtime, so it requires you to pass all the Soy files
to `SoyFileSet` at runtime in your application. SoySauce also supports this mode
for compatibility reasons (and it is also useful to implement dynamic
recompilation for an edit/refresh style of development), but because it is a
compiler it is signficantly slower the Tofu (you can expect `compileTemplates()`
to take about 50% longer than an identical call to `compileToTofu()`). For this
reason, there is an ahead of time compiler for `SoySauce`. See
[how to compile your template](dir.md#java) for more information.

Once you have precompiled all your templates, you can construct a `SoySauce`
object at runtime without `SoyFileSet` using 2 strategies.

<section class="zippy">

via `SoySauceBuilder`.

The `SoySauceBuilder` api allows you to construct a SoySauce object directly.

```java
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceBuilder;
...
SoySauce soySauce = new SoySauceBuilder().build();
```

See [Rendering from java](java.md#create-soysauce) for more details.

</section>

<section class="zippy">

via `Guice`

If you are using an application built around `Guice` it will be convenient to
use `com.google.template.soy.jbcsrc.api.PrecompiledSoyModule` to construct a
`SoySauce` object.

For example,

```java
SoySauce soy = Guice.createInjector(new PrecompiledSoyModule())
    .getInstance(Key.get(SoySauce.class, Precompiled.class));
```

This is useful if you are supplying plugins and plugin instances via `Guice`
multibinders.

</section>

At this point you have everthing compiling and loaded correctly, now it is time
to start rendering.

## 3. Migrate the rendering code

In your application you will need to migrate every interaction with `SoyTofu` to
instead use the `SoySauce` object you created above. The rendering APIs are very
similar so most of this should be fairly mechanical. Though depending on how
many callsites you have it may make sense to introduce a facade interface first
so you can easily switch back and forth.

... and that is it! of course besides better performance and new features there
are some behavior differences.

NOTE: the `SoyTofu.Renderer` has a `setData` overload that accepts a `SoyRecord`
object, but `SoySauce.Renderer` does not. This means that if you are
constructing `SoyData` objects to pass to the renderer that you will need to
switch to passing plain Java objects (maps, lists, strings, protos).

TIP: you may find it useful to use the `Shims` in the
`com.google.template.soy.jbcsrc.migration` package during this phase. It will
allow you to stop compiling a `SoyTofu` object prior to migrating all uses of
the interface. Similarly you can get access to a `SoySauce` object prior to
switching compilation.

## Incompatibilities

SoySauce is mostly compatible with Tofu, but some behaviors are slightly
different and some bugs were not ported.

### Different Exceptions will be thrown

When rendering in Tofu fails, a `SoyTofuException` will be thrown. In contrast,
SoySauce uses 'native' exception types, for example:

*   When a runtime type error occurs in Tofu, you will get a `SoyTofuException`
    wrapping a `SoyDataException`, in `SoySauce` you will get a
    `ClassCastException`
*   When you dereference a `null` value in Tofu you will get a
    `SoyTofuException`, in SoySauce you will get a `NullPointerException`

### Stricter runtime type checking

SoySauce has somewhat stricter runtime type checking. For example,

*   If you declare that a template has a param `{@param foos: list<string>}`
    Tofu will assert that the value is actually a `list.` SoySauce will do that,
    but it will also assert that `$foos[0]` is a string by checking it on access
    (this is the same strategy that java uses for generics). Depending on how
    the value was used this may cause a template that previously rendered
    succesfully to fail in SoySauce with a `ClassCastException`.
*   If a template has a typed parameter, e.g. `{@param href: uri}` and you call
    it with an expression of unknown type, Tofu has a bug such that it will not
    check that the parameter is a `uri` at runtime, but `SoySauce` will.

### Stricter `null` handling {#null-handling}

SoySauce is stricter about dereferencing null objects. For example, given the
expression `isNonnull($foo.bar.baz)` if `bar` is `null` then accessing `.baz` on
it should cause an error, and it does in SoySauce and the JS backend, however,
in Tofu this doesn’t happen (though there is a TODO), instead it only causes an
error if you perform certain operations with the result of the expression
(calling `isNonnull` and simple comparisons are the only thing you can do). An
appropriate fix would be to rewrite it as `isNonnull($foo.bar?.baz)`.


### Required parameter semantics

NOTE: If you use the SoyTemplate API for preparing parameters, this distinction
is irrelevant since it will consistently enforce required parameter syntax
across both backends.

SoySauce interprets 'required' template parameters slightly differently than
Tofu. Imagine this template:

```soy
{template .foo}
  {@param p: string}
  {$p}
{/template}
```

In Tofu, if you call `.foo` without passing `$p` there are a few things that can
happen:

*   If it is a top level call (Java code calling .foo), then you will get a
    SoyTofuException saying that a required parameter is missing.
*   If it is a Soy->Soy call then you will get null for `$p`

In SoySauce you always get `null`. We chose this option because it is more
internally consistent (Soy->Soy and java->Soy calls are treated equivalently)
and it is more consistent with the behavior of the JavaScript backend.

