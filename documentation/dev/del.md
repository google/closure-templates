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

For these situations Soy has a feature called `deltemplates` (short for
'delegate templates')

## Using delegate templates

For the above usecases, Soy has a set of commands `{deltemplate ...}`, `{delcall
...}` and `{modname ...}` to allow applications to declare certain calls to be
dynamic.

Delegate templates, or 'Deltemplates', allow you to define multiple
implementations of a template and choose one of them to call at render time.

## For most usecases, use `{modname }`

Deltemplates are typically registered using a `{modname }` declaration. This is
great for conditional features like experiments, or if you want to actually hide
certain features from certain users.

The `{modname XXX}` declaration allows you to associate a soy file with a
specific
[mod](http://g3doc/java/com/google/apps/framework/modulesets/g3doc/dev/pinto-module-system.md#mods).
For example, you could define several templates like this

### default_dialog.soy

```soy
{namespace my.project.dialog}

// A default deltemplate implementation, will be selected if none
// of the packages are active.
{deltemplate my.project.dialog}
  ...
{/deltemplate}
```

### foo_dialog.soy

```soy
{modname foo}
{namespace my.project.dialog_foo}

{deltemplate my.project.dialog}
  ...
{/deltemplate}
```

### bar_dialog.soy

```soy
{modname bar}
{namespace my.project.dialog_bar}

{deltemplate my.project.dialog}
  ...
{/deltemplate}
```

### main.soy

```soy
{namespace main}

{template render}
  ...
  {delcall my.project.dialog}...{/delcall}
  ...
{/template}
```

In this example there are 3 files that all define the same `deltemplate` and one
file that invokes it. The actual template that is rendered at runtime depends on
which `modname` is activated. If none are active then the default implementation
(the definition with no `modname`) will be rendered.

The algorithm for selecting the implementation to invoke is:

1.  Use an active non-default implementation, if there is one.
1.  Otherwise, use the default implementation.

### Activating a mod in Java

When rendering from Java, the set of active mods is determined by setting

```java
SoySauce.Renderer.setActiveDelegatePackageSelector(Predicate<String> predicate)
```

When deciding which `{deltemplate }` to invoke the predicate will be queried to
see which mods are active. Users can configure this on each call to the
renderer.

### Activating a mod in JS

In JS, it is expected that you will arrange to conditionally load at most one of
the `{modname ...}` gencode. It is an error to load more than one `{modname
...}` that defines the same `{deltemplate...}`

## In rare cases, use `variant`

Deltemplates can also be registered with a `variant` identifier. This is
typically used for complex cases involving multiple plugins, where code size and
bundling is important.

An example of Deltemplates registered with a `variant` identifier:

### text.soy

```soy
{namespace my.project.text}

{deltemplate my.project.renderContent variant="'text'"}
  {@param content: ?}
  <p>{$content}</p>
{/deltemplate}
```

### video.soy

```soy
{namespace my.project.video}

{deltemplate my.project.renderContent variant="'video'"}
  {@param content: ?}
  <video src=$content></video>
{/deltemplate}
```

### doc.soy

```soy
{namespace my.project.doc}

{deltemplate my.project.renderContent variant="'audio'"}
  {@param content: ?}
  <audio src=$content></audio>
{/deltemplate}
```

### main.soy

```soy
{namespace main}

{template render}
  {@param data: my.proto.Content}
  ...
  {delcall my.project.renderContent variant="$data.contentType"}
    {param content : $data.content /}
  {/delcall}
  ...
{/template}
```

This example demonstrates using a deltemplate where each version is registered
with a variant that describes the type of content it can render. Then at the
callsite we can dynamically select which template to render based on a type
descriminator in the data. This is useful for implementing plugin style systems.

This can be especially useful in Javascript to reduce code size, you could move
rarely used implementations into deltemplates with variants and then only load
the template implementations when you need to.

The algorithm for selecting the implementation to invoke is:

1.  Use the delegate implementation with matching variant, if there is one.
1.  Otherwise, use the delegate implementation with no variant.

## Using deltemplates with `variant` and `modname`

You can use the two features above together, and it is occasionally useful,
though it can get confusing quick! So do so sparingly.

When these two features are combined, the algorithm for selecting the
deltemplate implementation to all is:

1.  Use the delegate implementation with a matching variant and active mod if
    there is one
1.  Use the default delegate implementation with a matching variant if there is
    one
1.  Use the delegate implementation with no variant and an active mod if there
    is one
1.  Use the default delegate implementation with no variant.
