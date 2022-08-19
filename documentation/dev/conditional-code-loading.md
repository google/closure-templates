# Indirect Calls and Conditional Code Loading

[TOC]

## How do I make conditional template calls?

Using the `{if ..}` or `{switch ...}` command you can dynamically select
templates to `{call ...}` but sometimes this is undesireable, for example
because you might want to:

*   Avoid loading the templates code (in JS) if a given feature isn't enabled or
    is unlikely to be used.
*   Dynamically change the target of a call based on configuration that isn't
    known at compile time or can't be represented by logic in the template
    itself.

For these situations Soy has a feature called `modifiable` templates. A
`modifiable` template is a template that can have any number of `modifies`
templates associated with it. A `modifiable` template is called like an ordinary
template, but at runtime one of its `modifies` templates can be chosen to render
instead.

## For most usecases, use `{modname }`

`modifies` templates are typically registered using a `{modname }` declaration.
This is great for conditional features like experiments, or if you want to
actually hide certain features from certain users.

The `{modname XXX}` declaration allows you to associate a soy file with a
specific
[mod](http://g3doc/java/com/google/apps/framework/modulesets/g3doc/dev/pinto-module-system.md#mods).
For example, you could define several templates like this

### dialog.soy

```soy
{namespace my.project.dialog}

// A default implementation, will be selected if none of the packages are
// active.
{template dialog modifiable="true"}
  ...
{/template}
```

### foo_dialog.soy

```soy
{modname foo}
{namespace my.project.dialog_foo}

import {dialog} from 'dialog.soy';

{template dialogFoo visibility="private" modifies="dialog"}
  ...
{/template}
```

### bar_dialog.soy

```soy
{modname bar}
{namespace my.project.dialog_bar}

import {dialog} from 'dialog.soy';

{template dialogBar visibility="private" modifies="dialog"}
  ...
{/template}
```

### main.soy

```soy
{namespace main}

import {dialog} from 'dialog.soy';

{template render}
  ...
  {call dialog /}
  ...
{/template}
```

In this example there is a `modifiable` template and two `modifies` templates
that provide alternate implementations. The actual template that is rendered at
runtime depends on which `modname` is activated. If none are active then by
default the `modifiable` template will be rendered.

The algorithm for selecting the implementation to invoke is:

1.  Use a `modifies` template associated with an active `modname`, if there is
    one.
1.  Otherwise, render the `modifiable` template.

### Activating a mod in Java

When rendering from Java, the set of active mods is determined by setting

```java
SoySauce.Renderer.setActiveModSelector(Predicate<String> predicate)
```

When deciding which template to invoke the predicate will be queried to see
which mods are active. Users can configure this on each call to the renderer.

### Activating a mod in JS

In JS, it is expected that you will arrange to conditionally load at most one of
the `{modname ...}` gencode. It is an error to load more than one `{modname
...}` file that defines `modifies` templates for the same `modifiable` template.

## In rare cases, use `variant`

`modifies` templates can also be registered with a `variant` identifier. This is
typically used for complex cases involving multiple plugins, where code size and
bundling is important.

An example of `modifies` template registered with a `variant` identifier:

### text.soy

```soy
{namespace my.project.text}

{template renderContent modifiable="true" usevarianttype="string"}
  {@param content: ?}
  <p>{$content}</p>
{/template}
```

### video.soy

```soy
{namespace my.project.video}

import {renderContent} from 'text.soy';

{template renderVideoContent visibility="private" modifies="renderContent" variant="'video'"}
  {@param content: ?}
  <video src=$content></video>
{/template}
```

### doc.soy

```soy
{namespace my.project.doc}

import {renderContent} from 'text.soy';

{template renderDocContent visibility="private" modifies="renderContent" variant="'audio'"}
  {@param content: ?}
  <audio src=$content></audio>
{/template}
```

### main.soy

```soy
{namespace main}

import {renderContent} from 'text.soy';

{template render}
  {@param data: my.proto.Content}
  ...
  {call renderContent variant="$data.contentType"}
    {param content : $data.content /}
  {/call}
  ...
{/template}
```

In this example each version is registered with a variant that describes the
type of content it can render. Then at the callsite we can dynamically select
which template to render based on a type descriminator in the data. This is
useful for implementing plugin style systems.

This can be especially useful in Javascript to reduce code size, you could move
rarely used implementations into templates with variants and then only load the
template implementations when you need to.

The algorithm for selecting the implementation to invoke is:

1.  Use the `modifies` template with matching variant, if there is one.
1.  Otherwise, use the `modifiable` template with no variant.

## Using `modifies` templates with `variant` and `modname`

You can use the two features above together, and it is occasionally useful,
though it can get confusing quick! So do so sparingly.

When these two features are combined, the algorithm for selecting the
deltemplate implementation to all is:

1.  Use the template with a matching variant and active mod if there is one
1.  Use the template with a matching variant if there is one
1.  Use the template with no variant and an active mod if there is one
1.  Use the default `modifiable` template with no variant.
