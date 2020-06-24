# HTML Validation


The [HTML spec](https://html.spec.whatwg.org/multipage/) defines what is valid
HTML. There are many existing tools (both first-party and third-party) that can
validate plain HTML. In Soy, we also provide some HTML validation support by
default.

Different browsers treat invalid HTML snippets differently, but we want to
enforce a strict HTML mode that throws a compiler error for invalid HTML.

NOTE: We do not support the full HTML spec. The HTML spec is actually a
collection of separate specifications. Because Soy itself is complex (it is
turing-complete after all), any HTML validation we implement must be useful yet
not overly restrictive. Were we to implement the full HTML spec it would be
overly restrictive.

[TOC]

## Supported rules

For templates that enforce strict HTML mode, the following rules are supported
by Soy.

### Each block must contain balanced tags

```soy {.good}
// This is a valid HTML snippet.
{template .t}
  <div>
  {for $foo in $fooList}
    // Tags in this block should be closed within the same block. In this block,
    // <div> tag is closed and <input> tag is self-closing.
    <div>foo<p>bar</p></div><input>
  {/for}
  {call .foo}
    {param content kind="html"}
      // A param with kind="html" is also a block. Since the template sets
      // stricthtml to true, this part of the template should also close all
      // tags.
      <div><span><input></span></div>
    {/param}
  {/call}
  </div>
{/template}
```

This is an example of valid HTML snippet. In this example, in each `for` block,
every tag that is opened, must be closed (`input` is a [void
tag](https://www.w3.org/TR/HTML51/syntax.HTML#void-elements) and is
self-closing). The `param` block that explicitly sets `kind="html"` should also
contain self-closed tags. Note that a template is also a block and the `div` tag
at the very beginning has been closed at the end.

### Void elements

According to the [HTML
spec](https://www.w3.org/TR/2016/REC-html51-20161101/syntax.html#void-elements),
void elements only have a start tag; end tags must not be specified for void
elements. The compiler will enforce the following rules:

1.  Void elements must only have a start tag without an end tag. `</input>` is
    invalid HTML.
2.  Start tags for void elements can be self-closing (for example, `<input/>`)
    or normal (for example, `<input>`). Both are valid HTML.
3.  Start tags for non-void elements can *not* be self-closing. `<div/>` is
    invalid HTML.

NOTE: For tags with dynamic tag names, it is a little more complicated. In
particular, we assume users know what they are doing. See the Dynamic tag names
section for detailed examples.

### Dynamic tag names

For strict HTML mode, Soy supports dynamic tag names. The compiler enforces that
a HTML open tag with dynamic tag name (for example, `{$tagName1}`) must be
closed by a HTML close tag with the same tag name in the same block.

**Good code:**

```soy
// This is a valid HTML snippet.
{template .t}
  {@param tagName1: string}
  {@param tagName2: Foo}
  <{$tagName1}>
    <{$tagName2|fooToTag}>
    </{$tagName2|fooToTag}>
  </{$tagName1}>
{/template}
```

Note that print directives should also match, since it might change the
evaluated values during run time.

**Bad code:**

```soy {.bad}
// This is invalid since the close tag has an additional directive, while the
// open tag does not have any directives.
{template .t}
  {@param tagName: string}
  <{$tagName}>
  </{$tagName|fooToTag}>
{/template}
```

For self-closing tags with dynamic tag name, we simply trust the users that it
is valid.

**Good code:**

```soy {.good}
// This is valid HTML since we cannot statically decide if $tagName is a valid
// name for void elements. We simply trust the users that they are doing the
// right things.
{template .t}
  {@param tagName: string}
  // We trust users and assume that tagName is self-closing.
  <{$tagName}/>
{/template}
```

**Bad code:**

```soy {.bad}
{template .t}
  {@param tagName: string}
  // When we open $tagName (that is not self-closing), we assume that it is
  // not a void element, i.e., it must be closed.
  <{$tagName}>foo
{/template}
```

Matching raw text with dynamic expressions is not supported, even if the tag
name is compile-time constant.

**Bad code:**

```soy {.bad}
// This is invalid since we do not support matching static and dynamic tags.
{template .t}
  {let tagName: "div" /}
  <{$tagName}></div>
{/template}
```

### Control flow

The compiler supports tag balancing within control flows. The following example
is a simple but common use case.

**Good code:**

```soy {.good}
// An example of if conditions.
{template .t}
  {@param b: bool}
  {@param i: bool}
  {@param em: bool}
  {if $b}<b>{/if}
  {if $i}<i>{/if}
  {if $em}<em>{/if}
    content
  {if $em}</em>{/if}
  {if $i}</i>{/if}
  {if $b}</b>{/if}
{/template}
```

**Good code:**

```soy {.good}
// An example of switch conditions.
{template .t}
  {@param foo: string}
  {@param a: string}
  {@param b: string}
  {switch $foo}
    {case $a}
      <div>
    {case $b}
      // <input> is a void element.
      <p><input>
    {default}
      // <a> has been closed within this block.
      <em><a></a>
  {/switch}
  {switch $foo}
    {case $a}
      </div>
    {case $b}
      </p>
    {default}
      // <span> has been closed within this block.
      // </em> matches with the <em> in the previous switch branch.
      <span></span></em>
  {/switch}
{/template}
```

We only perform static analysis, so mixing if and switch conditions are
unsupported.

**Bad code:**

```soy {.bad}
{template .t}
  {@param case: int}
  {switch $case}
    {case 1}
    {case 2}
    {case 3}
      <em>
    {case 4}
  {/switch}
  {if $case == 3}</em>{/if}
{/template}
```

Also, we do not evaluate the expressions, so the following example will be
treated as an error.

**Bad code:**

```soy {.bad}
// Although each pair of open tag and close tag has the same conditions, we do
// not evaluate the expressions and cannot decide if they match or not.
{template .t}
  {@param foo: bool}
  {@param bar: bool}
  {@param tag: string}
  {if $foo}
    <b>       // Condition: $foo
  {elseif $bar}
    <i>       // Condition: not $foo and $bar
  {else}
    <{$tag}>  // Condition: not $foo and not $bar
  {/if}
  {if not $foo and not $bar}
    </{$tag}> // Condition: not $foo and not $bar
  {elseif $foo}
    </b>      // Condition: $foo
  {else}
    </i>      // Condition: not $foo and $bar
  {/if}
{/template}
```

**Bad code:**

```soy {.bad}
{template .t}
  {@param foo: bool}
  {let $bar: $foo}
  {if $foo}<b>{/if}
  // We do not evaluate $bar to $foo, so we don't know if these two are the
  // same conditions at this point. The compiler will throw an error.
  {if $bar}</b>{/if}
{/template}
```

On the other hand, complicated nested control flows are supported, as long as
all the expressions match by text.

**Good code:**

```soy {.good}
{template .t}
  {@param foo: bool}
  {@param bar: bool}
  {if $foo}
    <div>
    {if $bar}
      <p><input/>
    {/if}
  {/if}
  {if $foo}
    {if $bar}
      </p>
    {/if}
    </div>
  {/if}
{/template}
```

Also, the compiler is able to match common tags across all possible conditions.

**Good code:**

```soy {.good}
{template .t}
  {@param foo: string}
  {@param bar: bool}
  {@param a: string}
  {@param b: string}
  <div>
  // All possible branches have a close div tag.
  {if $foo}
    foo</div>
  {elseif $bar}
    bar</div>
  {else}
    xxx</div>
  {/if}

  // Similarly, all possible branches have a open div tag.
  {switch $foo}
    {case $a}
      <div>foo_a
    {case $b}
      <div>foo_b
    {default}
      <div>foo_x
  {/switch}
  </div>
{/template}
```

Besides that, in general, we do static analyses for the control flows, i.e., all
the conditions and tags within conditions should be exactly matched. The
following templates which contain control blocks that partially match the
previous blocks, are not supported.

**Bad code:**

```soy {.bad}
{template .t}
  {@param foo: bool}
  {if $foo}
    <div><div>
  {/if}
  {if $foo}
    </div>
  {/if}
  {if $foo}
    </div>
  {/if}
{/template}
```

**Bad code:**

```soy {.bad}
{template .t}
  {@param foo: bool}
  {@param bar: bool}
  {if $foo}
    <div>
  {else}
    {if $bar}
      <div><div>
    {else}
      <div>
    {/if}
  {/if}
  // This matches with the extra tag in the first IfNode
  {if $foo}
  {else}
    {if $bar}
      </div>
    {else}
    {/if}
  {/if}
  // This matches the common prefix in the first IfNode
  </div>
{/template}
```

### `for`

The following template is technically a valid HTML. The `for` loop opens three
div tags, and then we manually close all of them after the loop. However, we
decided to not support this case since the conditions of `for` could be dynamic
and there is no easy way to statically decide if this template a is valid HTML.

In this particular example, the compiler will report an exception at the
location of `<div>` tag in for loop. It will complain that this HTML open tag is
not closed within the current block.

**Bad code:**

```soy {.bad}
{template .t}
  {for i in range(3)}
    <div>
  {/for}
  </div></div></div>
{/template}
```

You can use a recursive template to create nesting:

**Good code:**

```soy {.good}
{template .t}
  {@param level: int}
  {@param content: html}
  {if $level > 0}
    <div>
      {call .t}
        {param level: $level - 1 /}
        {param content: $content /}
      {/call}
    </div>
  {else}
    {$content}
  {/if}
{/template}
```

The following template is another example that is a valid HTML but is not
supported by the compiler.

**Bad code:**

```soy {.bad}
{template .t}
  {@param a: list<string>}
  {for $x in $a}
    {if isFirst($x)}<ul>{/if}
    <li>{$x}
    {if isLast($x)}</ul>{/if}
  {/for}
{/template}
```

`isFirst` and `isLast` are [built-in commands](control-flow#for) that check the
position of the current iterator. Although this template produces valid HTML (it
opens and closes `<ul>` exactly once if the list is non-empty), supporting this
pattern adds an additional layer of complexity to the compiler. It requires
checking function names, validating this particular AST structure, and do
special handling for these functions.

For this example, we recommend you to use the following template. It renders
exactly the same HTML, and is supported by the compiler.

**Good code:**

```soy {.good}
{template .t}
  {@param a: list<string>}
  {if length($a) > 0}
    <ul>
    {for $x in $a}
      <li>{$x}
    {/for}
    </ul>
  {/if}
{/template}
```

### Optional tags

According to the HTML spec, some tags [can be
omitted](https://www.w3.org/TR/html5/syntax.html#optional-tags). In particular,
if certain criteria are met, the HTML parser can imply the start tags and/or end
tags.

Soy supports part of these rules. The major differences between what we support
and the HTML spec:

*   **Start tags must be presented.** In HTML spec, it is possible to omit start
    tags under some circumstances. An example is that `<html>` and `<head>`
    might be omitted. However, due to the rendering model of Soy, we don't
    support that.
*   **Strict criteria are not enforced.** In HTML spec, most of the end tags can
    be omitted based on the content model. For example, `</body>` may be omitted
    if the `<body>` is not immediately followed by a comment. Soy assumes all
    unclosed optional tags are fine. Additional conditions such as "is not
    immediately followed by a comment" are not enforced.

Some examples are:

**Good code:**

```soy {.good}
{template .t}
  {@param foo: bool}
  {@param bar: bool}
  <html>
  <head>
  <ul>
    // optional tag that does not close
    <li>foo
    <li>
    // optional tag that closes and contains if nodes as its children
    <li>b{if $foo}<b>{/if}a{if $bar}<i>{/if}r{if $bar}</i>{/if}{if $foo}</b>{/if}</li>
    // optional tag that closes
    <li>baz</li>
    <li></li>
    // optional tag that does not close and contains if nodes as its children
    <li>b{if $foo}<b>{/if}a{if $bar}<i>{/if}r{if $bar}</i>{/if}{if $foo}</b>{/if}
  </ul>
  // html and head are automatically closed at the end
{/template}
```

Mixing complicated control flows and optional tags is also supported. For
example...

**Good code:**

```soy {.good}
{template .t}
  {@param foo: bool}
  {@param bar: bool}
  <ul>
    <li>foo
    <li>b
      {if $foo}<b>{/if}
        a
        {if $bar}<i>{/if}
          r
        {if $bar}</i>{/if}
      {if $foo}</b>{/if}
    <li>baz</li>
    <li>
    {if $foo}<li>{/if}{if $foo}</li>{/if}
  </ul>
{/template}
```

### Foreign elements

HTML allows elements from non-HTML namespaces (such as MathML and SVG) to appear
in some contexts. MathML is *not* supported since it is deprecated and has
already been removed from Chrome. SVG is quite popular and supported.

Notably SVG uses XML spec, which means all tags must be closed and every tag is
allowed to be self-closing.

**Good code:**

```soy {.good}
{template .foreign_elements_simple}
  <svg>
    <path/>
    <path></path>
    <rect/>
    <rect></rect>
  </svg>
{/template}
```

**Good code:**

```soy {.good}
{template .foreign_elements_control_flow}
  {@param foo: bool}
  <svg>
    <path/>
    <path></path>
    {if $foo}<rect/>{/if}
    <rect/>
    <rect></rect>
    {if $foo}<path>{/if}
    {if $foo}</path>{/if}
  </svg>
{/template}
```

We also enforce that SVG tags can only be opened and closed within the same
block.

**Bad code:**

```soy {.bad}
{template .fail_foreign_elements_across_block}
  {@param foo: bool}
  {if $foo}
    <svg>
  {else}
    <svg>
  {/if}
  </svg>
{/template}
```

## Known Issues

As we said before, we do not support full HTML spec. There are several general
limitations/complexities to keep in mind:

*   Users write recursive templates, so the depth and structure of the DOM tree
    is fully dynamic.
*   The Soy rendering model provides very limited information about what we are
    rendering. For example, we don't know the current `DOCTYPE`, the current
    `Content-Type headers`, or which `<meta>` tags have been sent/specified. So
    we need to not care about the specifics that are controlled by these
    details.
*   Users render tag names dynamically, so we won't necessarily know the
    specific tag names statically.
*   Soy allows for opaque content transclusions of HTML content, so we won't
    necessarily know (statically) the number of predecessors of a given tag.

On the other hand, there are some things we could theoretically validate, but we
don't currently since we believe them to be of limited utility. An example is
HTML [content models](https://www.w3.org/TR/html5/dom.html#content-models).
Supporting full content models is infeasible in Soy; we can still add separate
rules such as don't put `<div>` inside `<p>` (`p` tag should only
[contain phrasing contents](https://dev.w3.org/html5/spec-preview/the-p-element.html#the-p-element)),
but enforcing this in Soy does not bring many benefits.

## Disabling HTML Validation

HTML validation is enabled by default. To disable it:

*   Add `stricthtml="false"` to your templates or namespaces.
    *   Setting strict HTML mode in namespaces will set a default value for all
        HTML templates in this file.
    *   Each HTML template can still override the default value inheriting from
        the namespace.
    *   Non-HTML templates will not be affected at all.
    *   The compiler will report errors for invalid HTML templates.

In the following example, template `foo` will enforce strict HTML mode, but
templates `bar` and `baz` will not.

```soy
// By default, all HTML templates will enable stricthtml mode.
{namespace ns}

// This template does not override the default value, so it will have stricthtml
// mode enabled.
{template .foo}
...
{/template}

// This is a non-HTML template, and stricthtml mode does not apply for it.
{template .bar kind="text"}
...
{/template}

// This template override the default value and disable stricthtml mode.
{template baz stricthtml="false"}
...
{/template}
```

To enforce `stricthtml` in all templates, add a custom [Soy
conformance](../dev/conformance) rule
`com.google.template.soy.conformance.RequireStrictHtml`.
