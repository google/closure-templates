# Raw Text Commands


[TOC]

## literal {#literal}

Syntax:

```soy
{literal} ... {/literal}
```

This command allows you to include a literal block of raw text, possibly
including special characters like braces. The rendered output is exactly what
appears in the `literal` block. Note that this means that absolutely no
processing happens within a `literal` block: no HTML escaping, no line joining,
no removal of indentation, no parsing for template commands, and no parsing for
comments.

## Special Characters

Use the following special character commands to add raw text characters:

Special Character | Equivalent Raw Text
----------------- | --------------------------------------
`{sp}`            | space
`{nil}`           | empty string
`{\r}`            | carriage return
`{\n}`            | new line (line feed)
`{\t}`            | tab
`{lb}`            | left brace
`{rb}`            | right brace
`{nbsp}`          | non-breaking space (Unicode character)

Follow these heuristics when deciding when to use `{sp}` and `{nil}`:

-   `{sp}`: Use `{sp}` to insert a space character at the beginning or end of a
    line, in situations where the line-joining algorithm would not normally
    insert a space (specifically, at line-joining locations bordered by an HTML
    or template tag on either side). See the [too few spaces
    example](#too-few-spaces) below.

-   `{nil}`: If a line joining location normally adds a space, but you want to
    make the lines join without a space, then add `{nil}` at the end of the
    first line (most common) or the beginning of the second line. While the
    `{nil}` itself turns into an empty string, it adds a template tag at the
    line-joining location, which eliminates the default single space. See the
    [too many spaces example](#too-many-spaces) below.

-   `{nbsp}`: Use `{nbsp}` to insert a Unicode character for non-breaking space.
    `{nbsp}` is never stripped because it's not treated as whitespace by Soy.
    See the [non-breaking space example](#non-breaking-space) below.

## Line Joining {#line-joining}

Soy is optimized for producing HTML documents, and in such documents
[whitespace is not significant](https://www.w3.org/TR/html4/struct/text.html#h-9.1)
(except in certain circumstances like `<pre>` tags, `whitespace:pre` styles).
Because of this, Soy has implemented a whitespace removal algorithm (called
'line joining') to allow template authors to use whitespace to format their
code, but not actually render whitespace characters.

NOTE: This default behavior can be disabled on a per-`template` basis by setting
the `whitespace='preserve'` attribute. See
[template attributes](templates.md#template) for more information.

For example, here is a standard template:

```soy
{template .foo}
  <div class=outer>
    <div class=inner>
      Content
    </div>
  </div>
{/template}
```

In this template that author has used leading whitespace characters and newlines
to format the content. When rendered however it will display as:

```html
<div class=outer><div class=inner>Content</div></div>
```

Soy has removed all the whitespace. This is often desired because it leads to
smaller code and smaller rendered documents, however it can also occasionally
lead to unexpected results. To understand why, let's look at how lines are
joined below.

### Line joining algorithm {#line-joining-algorithm}

1.  Remove all line terminators and whitespace at the beginning and end of
    lines, including spaces preceding an end-of-line comment.

    *   `{call .foo /} \n` will be converted to `{call .foo /}\n`

1.  Remove empty lines that consist of only whitespace.

1.  Consecutive lines are joined according to the following heuristic:

    *   If the join location borders a Soy Command (something in curly braces
        `{` `}`) or HTML tag (something in triangle brackets `<` `>`) on either
        side, the lines are joined with **no space**.

        **Note:**: This differs from how pure HTML is rendered outside of Soy
        ([spec](https://www.w3.org/TR/html4/struct/text.html#h-9.1)).

    *   If the join location does not border a Soy Command or HTML tag on either
        side, the lines are joined with **exactly one space**.

1.  Within HTML tags, all whitespace (include newlines) is removed and replaced
    with the bare minimum to avoid parsing errors (e.g. we would retain a space
    between `id="foo"` and `class="bar"`; see examples below).

    **Note:**: This does not apply **within** quoted attribute values; inside
    the quotes, Soy will apply the regular line joining algorithm (lines are
    joined with a space, unless bordering a curly brace or angle bracket).

### Line Joining Examples

#### No spaces added

The following lines:

```soy
{call .foo /}
Hi
```

will be joined together as: `{call .foo /}Hi` with no space, since the line join
location borders a curly brace command.

Similarly,

```soy
<h2>
  Heading
</h2>
```

will be joined together as `<h2>Heading</h2>` with no spaces, since both line
joining locations are adjacent to html tags. Note that the indentation does
**not** cause the lines to be joined with a space.

#### Single space added

If a line join location does not border an HTML tag or Soy command, it will be
joined with one space. For example, the body of the following message:

```soy
{msg desc="desc"}
Hello, my name is
Foo.
{/msg}
```

will be rendered as: `Hello, my name is Foo.`, with a space inserted between
"is" and "Foo". Spaces will not be added at the start and end of the message
though, since those line join locations are adjacent to the `msg` curly braces.

#### HTML Tags

Within HTML tags, the Soy compiler will use the minimal amount of whitespace
needed to separate HTML attributes and tag names.

For example:

```soy
<div
  class="myClass"
  jsname="foo"   data="bar"
>
</div>
```

will be rendered as: `<div class="myClass" jsname="foo" data="bar"></div>`.

#### Inside Quoted Attribute Values

The normal line joining algorithm applies inside quoted attribute values.

The following:

```soy
<div
  class="myClass1
    myClass2"
>
</div>
```

will be joined with a space: `<div class="myClass1 myClass2"></div>`.

Conversely, this example:

```soy
<div
  class="{$myClassName1}
   {$myClassName2}"
>
</div>
```

will be joined with no space, because the join location borders a command tag
(curly brace). An {sp} tag is necesssary here to prevent the classes from being
merged:

```soy
<div
  class="{$myClassName1}
   {sp}{$myClassName2}"
>
</div>
```

### Tips & tricks for common mistakes

#### Too few spaces {#too-few-spaces}

The following is a common example of where line joining has an unintended effect

```soy
Hello
<a href="...">World</a>
```

There is a newline after 'Hello' so it will be removed and the two lines will be
joined, in this case the join location borders on an HTML tag so this will be
interpreted as if the author had written this:

```soy
Hello<a href="...">World</a>
```

Which is probably not what the author wants. To fix, a `{sp}` command should be
inserted either immediately after 'Hello' or before '<a ...'

```soy
Hello
{sp}<a href="...">World</a>
```

#### Too many spaces {#too-many-spaces}

Here is an example where the line joining algorithm will preserve a whitespace
character when it is undesired:

```soy
<a href="{$url}" class="
  foo bar
">
```

will be interpreted as if the author had written this:

```soy
<a href="{$url}" class=" foo bar ">
```

Note the extra whitespace characters at the beginning and end of the `class`
attribute. These aren't harmful but they may be undesired, in which case the
`{nil}` command can be used to eliminate it:

```soy
<a href="{$url}" class="{nil}
  foo bar{nil}
">
```

Now the line joining algorithm will produce this:

```soy
<a href="{$url}" class="foo bar">
```

#### Non-breaking space

Use `{nbsp}` where you don't want the space to be wrapped. In HTML, it's also
possible to use the `&nbsp;` entity but `{nbsp}` works in all contexts and
produces a single Unicode character.

```soy
{let .price kind="text"}
  {$amount}{nbsp}{$currency}
{/let}
```
