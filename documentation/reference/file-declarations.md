# File Declarations

[TOC]

## namespace {#namespace}

Syntax (basic form):

```soy
{namespace <namespace>}
```

With all optional attributes:

```soy
{namespace <namespace> requirecsspath="<CSS_FILE>" cssprefix="<PREFIX>" requirecss="<NAMESPACE>.<CSS_ELEMENT>" cssbase="<NAMESPACE>.<CSS_BASE>"}
```

These are the `namespace` tag's attributes:

*   `requirecsspath` takes a list of absolute and/or relative paths for CSS
    files, without their file extensions. These can be either GSS or Sass files.
    This does NOT have any autoprefix behavior. Use of `cssbase` or `cssprefix`
    is required to autoprefix.

*   `cssprefix`: takes an explicit prefix to use for autoprefixing in
    [`css` function](functions.md#css) calls.

*   `requirecss`: Deprecated. Use `requirecsspath` instead.

    Takes a list of CSS namespaces (dotted identifiers). This is used to add
    `@requirecss` annotations in the generated JavaScript. Also, if there is no
    `cssbase` attribute, the first `requirecss` namespace can be used for
    autoprefixing in [`css` function](functions.md#css) calls.

*   `cssbase`: Deprecated. Use `cssprefix` instead.

    Takes a single CSS namespace (dotted identifier). This is used for
    autoprefixing in [`css` function](functions.md#css) calls.

This command is required at the start of every template file. It declares the
namespace for the file, which serves as the common prefix for the full name of
every template in the file.

Soy allows multiple files to have the same namespace. However, Closure Library
and Closure Compiler do not allow it, so if you're using Soy in JavaScript
together with other Closure technologies, avoid having the same namespace in
multiple files.

## import

This command allows you to import templates and external functions from other
Soy files, and protos.

Syntax:

```soy
import {button, render as fooRender} from 'path/to/soy/file/foo.soy';
```

You can also import all templates from other soy files using `*` and grouping
them with a name.

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
import * as fooMagic from 'path/to/soy/file/foo.soy';
...
{call fooMagic.button}
  ...
{/call}
```

###### Element Composition {.pg-tab}

```soy
import * as fooMagic from 'path/to/soy/file/foo.soy';
...
<{fooMagic.button()} />
```

</section>

Import statements should be sorted by path.

**Note:** Always prefer imports over referencing fully qualified names or using
aliases (both are now deprecated and will soon be banned; we are in the process
of migrating all existing users to use imports).

### Template Imports

In the above example, `'foo.soy'` needs to contain:

```soy
{template button}
  ...
{/template}

{template render}
  ...
{/template}
```

The syntax for calling imported templates is:

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
{call button /}
{call fooRender /}
```

###### Element Composition {.pg-tab}

```soy
<{button()} />
<{fooRender()} />
```

</section>

## alias (DEPRECATED; will be deleted soon) {#alias}

**Warning:** The `alias` command will be deprecated soon. Use
[`import`](#import) instead.

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

This deprecated feature still works for the ID parameter to the
[`xid()` function](functions#xid).

## delpackage {#delpackage}

Syntax:

```soy
{delpackage <delegate_package_name>}
```

This command belongs at the start of a template file, before the `namespace`
tag. It is one of the two ways to use delegate templates. For details, see the
section on [using delegate templates with delpackage](delegate-templates.md).
