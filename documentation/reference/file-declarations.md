# File Declarations


<!--#include file="commands-blurb-include.md"-->

This section describes the file declaration commands.

[TOC]

## namespace {#namespace}

Syntax (basic form):

```soy
{namespace <namespace>}
```

With all optional attributes:

```soy
{namespace <namespace> requirecss="<NAMESPACE>.<CSS_ELEMENT>" cssbase="<NAMESPACE>.<CSS_BASE>"}
```

These are the `namespace` tag's attributes:

<!--#include file="common-attributes-include.md"-->

This command is required at the start of every template file. It declares the
namespace for the file, which serves as the common prefix for the full name of
every template in the file.

Soy allows multiple files to have the same namespace. However, Closure Library
and Closure Compiler do not allow it, so if you're using Soy in JavaScript
together with other Closure technologies, avoid having the same namespace in
multiple files.

## alias {#alias}

Syntax:

```soy
{alias <namespace>}
{alias <namespace> as <identifier>}
```

This command belongs at the start of the file, after the `namespace` tag. You
can include any number of `alias` declarations. Each declaration aliases the
given namespace to an identifier. For the first syntax form above, the alias is
the last identifier in the namespace. For the second syntax form, the alias is
the identifier you specify. When you `call` a template in an aliased namespace,
you don't need to type the whole namespace, only the alias plus the template's
partial name.

For example, if you declare

```soy
{alias long.namespace.root.projectx.mymodule.myfeature as myfeature}
{alias long.namespace.root.projectx.foomodule.utils as fooUtils}
```

then you can replace the calls

```soy
{call long.namespace.root.projectx.mymodule.myfeature.myTemplate /}
{call long.namespace.root.projectx.foomodule.utils.someHelper /}
```

with

```soy
{call myfeature.myTemplate /}
{call fooUtils.someHelper /}
```

Delegate templates have their own full names (not a partial name prefixed by the
namespace), so `alias` does not affect delegate calls (`delcall`).

In addition to templates, the `alias` directive also applies to all places where
named identifiers are used:

*   [Global references](expressions#globals), including proto enum literals
*   The identifiers in [proto init](expressions#proto-initialization)
    expressions
*   The named types, especially [proto type](types#proto)
*   The ID parameter to the [`xid()` function](functions#xid)

## delpackage {#delpackage}

Syntax:

```soy
{delpackage <delegate_package_name>}
```

This command belongs at the start of a template file, before the `namespace`
tag. It is one of the two ways to use delegate templates. For details, see the
section on [using delegate templates with delpackage](delegate-templates.md).
