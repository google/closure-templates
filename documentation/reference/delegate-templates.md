# Delegate templates

IMPORTANT: Delegate templates are deprecated. Take a look at
[modifiable templates](modifiable-templates.md) instead.

Delegate templates allow you to write multiple implementations of a template and
choose one of them at render time. Delegate templates are defined and called
using `deltemplate` and `delcall`, which have syntax similar to `template` and
`call`.

There are two independent ways to use delegates, differing in how you control
which delegate implementation is called. Delegates associated with mods
(`modname`) are appropriate for cases where you don't intend to send code for
unused delegate implementations to the client (for example, an experiment whose
code is only sent to a small subset of users.) Delegates with the `variant`
attribute are appropriate for finer control of delegate selection at the call
site.

## Basic structure

Delegate templates must have one and only one *default implementation*. The
default is any deltemplate which is not in a file with the `{modname}` file
declaration, and which does not have the `variant` attribute set to anything
other than empty string. The default can be empty.

The default must either be in the same file as the `delcall`, or in a file that
is imported. The import can possibly be unused, in which case you can prefix
"unused" to the imported name to suppress Soy unused import warnings.

`my/project/default.soy`

```soy
{namespace my.project.default}

{deltemplate my.project.experiment}
 // This is the default.
{/deltemplate}
```

`my/project/foo.soy`

```soy
{namespace my.project.code}

import * as unusedDeltemplateDefaults from 'my/project/default.soy`

{template project}
  {delcall my.project.experiment /}
{/template}
```

The default can be overriden at runtime using modname or variants.

[TOC]

## Delegate templates (with modname)

Delegates associated with mods (`modname`) are appropriate for cases where you
don't intend to send code for unused delegate implementations to the client (for
example, an experiment whose code is only sent to a small subset of users.)

`main.soy` syntax:

```soy
{namespace ...}

/** Caller (basic template, not delegate template). */
{template aTemplate}
  {delcall aaa.bbb.myButton allowemptydefault="true" data="..." /}
{/template}

/** Default implementation. */
{deltemplate aaa.bbb.myButton}
  ...
{/deltemplate}
```

`experiment.soy` syntax:

```soy
{modname MyExperiment}
{namespace ...}

/** My experiment's implementation. */
{deltemplate aaa.bbb.myButton}
  ...
{/deltemplate}
```

The implementations must appear in different files, and each file other than the
default implementation must declare a `modname`. This is the identifier used to
select the implementation at usage time.

The delegate template names are not within the file's namespace; namespaces only
apply to basic templates. Instead, delegate template names are just strings that
are always written in full. They can be any identifier or multiple identifiers
connected with dots. The namespace of any delegate template file, however, must
be different from the default file and any other included delegate template
file.

Template files can have an optional `modname` declaration at the top, just above
the `namespace` declaration. And multiple files can have the same `modname`
name, putting them all within the same delegate package. If a delegate template
is defined in a file without `modname`, then it is a default implementation. If
a delegate template is defined in a file with `modname`, then it is a
non-default implementation.

At render time, when a delegate call needs to be resolved, Soy looks at all the
"active" implementations of the delegate template and uses the implementation
with the highest priority. Basically, this means:

1.  Use a non-default implementation, if there is one.
2.  Otherwise, use the default implementation.

A default implementation must exist, even if it's empty. Any file with a delcall
must also import the file containing the default.

What counts as an "active" implementation depends on the backend in use. In
JavaScript, an active implementation is simply an implementation that is defined
in the JavaScript files that are loaded. Ship only the generated JavaScript
files for the active `modname`s.

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

### Special case: a modded Soy template B under another modded Soy template A

Please note that it is an error for two deltemplates to be installed at runtime
with the same priority. Therefore, do not define the default implementation of
deltemplate B within a modname. This would give B's default implementation the
same priority as B's non-default (modname) implementations; essentially, B would
not have a default implementation.

So instead, put deltemplate B into a file without a modname. This will allow the
variant (with a modname) to override it.

## Delegate Templates (with variant)

Delegates with the `variant` attribute are appropriate for finer control of
delegate selection at the call site.

Syntax:

```soy
/** Caller (basic template, not delegate template). */
{template aTemplate}
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

The variant in a `deltemplate` command must be a string literal containing an
identifier. If no variant is specified, then it defaults to the empty string.
The variant in a `delcall` command can be an arbitrary expression, as long as it
evaluates to a string at render time.

At render time, when a delegate call needs to be resolved,

1.  Use the delegate implementation with matching variant, if there is one.
2.  Otherwise, use the delegate implementation with no variant, if there is one.
3.  Otherwise, if the `delcall` has the attribute `allowemptydefault="true"`,
    then the call renders to the empty string.
4.  Otherwise, an error occurs.
