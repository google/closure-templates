[TOC]

## What are templates?

Templates are the basic unit of composition in Soy. Each template defines a
function that can be used for rendering. For example,

```soy
{namespace my.project.namespace}
{template .hello}
  {@param name: string}
  Hello, <b>{$name}</b>
{/template}
```

This file defines a template called `my.project.namespace.hello` that can take a
single parameter called `name` and it will render a simple snippet of HTML.

Templates can be thought of like functions in other programming languages. You
can define templates, give them names and call them. Each one defines some
content to be rendered and takes some parameters.

## How to write a template

To write a template, first define a `.soy` file with a namespace declaration

```soy
{namespace my.namespace}
```

Namespaces in Soy are much like namespaces in Closure, or packages in Java.
After defining your namespace, you can start defining templates within that
namespace:

```soy
{namespace my.namespace}

{template .hello}
{/template}
```

Each `template` can be thought of as a function that renders content. Here we
define an empty template, which isn't very useful. So let's add some content:

```soy
{namespace my.namespace}

{template .hello}
  Hello, world!
{/template}
```

Now the template will render the text `Hello, world!` when rendered.

### How to use template parameters

Each template can define a number of parameters that it can take. These are
perfectly analagous to function arguments in other languages. They are declared
via the `{@param ...}` syntax.

For example:

```soy
{namespace my.namespace}

{template .hello}
  {@param name: string} /* The name to greet */
  Hello, {$name}!
{/template}
```

Now our template has declared a parameter called `$name` which has the type
`string`. (See the reference section on [types](../reference/types.md) for
information on all the valid parameter types)

### Injected parameters

Templates can also use injected parameters. These are useful if there is some
global value that many different templates may need to access. Rather than
passing these parameters through every template, they can be registered as
'injected parameters' and then accessed like this.

```soy
{namespace my.namespace}

{template .hello}
  {@param name: string}
  {@inject partyTime: bool}
  <span class="{$partyTime ? 'party' : ''}">
  Hello, {$name}!
  </span>
{/template}
```

This uses an injected param named `partyTime` that will enable new styles
through the application. In this template it is used to conditionally add a css
class name to a wrapper `span`.

#### Legacy syntax

**WARNING**: New projects should not use this syntax

You may also occasionally see the legacy syntax for injected params that are
defined via `$ij.<name>` syntax.

For example:

```soy
{namespace my.namespace}

{template .hello}
  {@param name: string}
  <span class="{$ij.partyTime ? 'party' : ''}">
  Hello, {$name}!
  </span>
{/template}
```

This is identical to the above example, but instead of declaring the parameter
with a type along with the template definition, it is declared where it is used.

## How to call a template

Now that we have defined a template, we probably want to call it. Templates can
be called from their host languages (see how to call from [Java](java.md) and
[JavaScript](js.md)) or from other Soy templates.

```soy
{namespace my.other.namespace}
import * as myNamespace from 'path/to/hello.soy';

{template .helloEveryone}
  {@param names: list<string>}
  <ul>
    {for $name in $names}
      <li>
      {call myNamespace.hello}
        {param name : $name/}
      {/call}
      </li>
    {/for}
  </ul>
{/template}
```

This template uses the `{call ...}` command to invoke the template we defined
above.

## How to render non-HTML content

The examples above demonstrate how to render simple snippets of HTML which is a
key Soy use case. But sometimes you want to render different kinds of content.
For this, each template has a `kind` parameter (the default value is
`kind="html"`).

For example,

```soy
{template .partyAttrs kind="attributes"}
  class="party-time" data-party="on"
{/template}
```

This template will render a set of HTML attribute value pairs. This is useful
because it tells callers what kind of content to expect and it tells the
autoescaper what kind of content is being produced.

## More?

Templates have several other attributes and advanced features listed in the
[reference](../reference/templates.md).
