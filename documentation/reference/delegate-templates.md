# Delegate templates


<!--#include file="commands-blurb-include.md"-->

This chapter describes the delegate template commands.

Delegate templates allow you to write multiple implementations of a template and
choose one of them at render time. Delegate templates are defined and called
using `deltemplate` and `delcall`, which have syntax similar to `template` and
`call`.

There are two independent ways to use delegates, differing in how you control
which delegate implementation is called. Delegates with delegate packages
(`delpackage`) are appropriate for cases where you don't intend to send code for
unused delegate implementations to the client (for example, an experiment whose
code is only sent to a small subset of users.) Delegates with the `variant`
attribute are appropriate for finer control of delegate selection at the call
site.

[TOC]

## Delegate templates (with delpackage)

Delegates with delegate packages (`delpackage`) are appropriate for cases where
you don't intend to send code for unused delegate implementations to the client
(for example, an experiment whose code is only sent to a small subset of users.)

`main.soy` syntax:

```soy
{namespace ...}

/** Caller (basic template, not delegate template). */
{template ...}
  {delcall aaa.bbb.myButton allowemptydefault="true" data="..." /}
{/template}

/** Default implementation. */
{deltemplate aaa.bbb.myButton}
  ...
{/deltemplate}
```

`experiment.soy` syntax:

```soy
{delpackage MyExperiment}
{namespace ...}

/** My experiment's implementation. */
{deltemplate aaa.bbb.myButton}
  ...
{/deltemplate}
```

The implementations must appear in different files, and each file other than the
default implementation must declare a delegate package name (`delpackage`). This
is the identifier used to select the implementation at usage time.

The delegate template names are not within the file's namespace; namespaces only
apply to basic templates. Instead, delegate template names are just strings that
are always written in full. They can be any identifier or multiple identifiers
connected with dots.

Template files can have an optional `delpackage` declaration at the top, just
above the `namespace` declaration. And multiple files can have the same
`delpackage` name, putting them all within the same delegate package. If a
delegate template is defined in a file without `delpackage`, then it is a
default implementation. If a delegate template is defined in a file with
`delpackage`, then it is a non-default implementation.

At render time, when a delegate call needs to be resolved, Soy looks at all the
"active" implementations of the delegate template and uses the implementation
with the highest priority. Basically, this means:

1.  Use a non-default implementation, if there is one.
2.  Otherwise, use the default implementation, if there is one.
3.  Otherwise, if the `delcall` has the attribute `allowemptydefault="true"`,
    then the call renders to the empty string.
4.  Otherwise, an error occurs.

Due to the third case, it is legal to render a delegate template that has no
default implementation, as long as the `delcall` has `allowemptydefault="true"`.

What counts as an "active" implementation depends on the backend in use. In
JavaScript, an active implementation is simply an implementation that is defined
in the JavaScript files that are loaded. Ship only the generated JavaScript
files for the active `delpackage`s.

In Java, use `SoySauce.Renderer#setActiveDelegatePackageSelector` to set the
active implementations. For example, with the example template code above, call

```java
soySauce.newRenderer(...)
    .setActiveDelegatePackageSelector("MyExperiment"::equals)
    .setData(...)
    .render()
```

This will use the non-default implementation of `aaa.bbb.myButton` from the
`MyExperiment` delegate package. On the other hand, if you omit the call to
`setActiveDelegatePackageSelector()`, or if you pass a set not including
`MyExperiment`, it will use the default implementation of `aaa.bbb.myButton`.

In either backend, it is an error to have more than one active implementation at
the same priority (for example, multiple active non-default implementations).

## Delegate Templates (with variant)

Delegates with the `variant` attribute are appropriate for finer control of
delegate selection at the call site.

Syntax:

```soy
/** Caller (basic template, not delegate template). */
{template ...}
  {delcall aaa.bbb.myButton variant="$variantToUse" /}
{/template}

/** Implementation 'alpha'. */
{deltemplate aaa.bbb.myButton variant="'alpha'"}
  ...
{/deltemplate}

/** Implementation 'beta'. */
{deltemplate aaa.bbb.myButton variant="'beta'"}
  ...
{/deltemplate}
```

The delegate template names are not within the file's namespace; namespaces only
apply to basic templates. Instead, delegate template names are just strings that
are always written in full. They can be any identifier or multiple identifiers
connected with dots.

The variant in a `deltemplate` command must be a string literal containing an
identifier. If no variant is specified, then it defaults to the empty string.
The variant in a `delcall` command can be an arbitrary expression, as long as it
evaluates to a string at render time.

At render time, when a delegate call needs to be resolved,

1.  Use the delegate implementation with matching variant, if there is one.
2.  Otherwise, use the delegate implementation with no variant, if there is one.
3.  Otherwise, an error occurs.
