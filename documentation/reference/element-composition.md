# Element Composition


[TOC]

NOTE: This is not generally available. Please do not use this feature yet.

## HTML-tag templates {#html-tag-templates}

HTML-tag templates are templates that declare a `kind` of `html<[TAG_NAME]>`.
The `TAG_NAME` can be any tag name such as `div` or `span`. HTML-tag templates
must contain one visible element or an element composition call (see below).

The following are valid HTML-tag templates:

```soy
{template example kind="html<div>"}
  <div></div> // One visible element
{/template}

{template example2 kind="html<div>"}
  <{example3()}></> // Element composition call
{/template}
```

The following are not:

```soy {.bad}
// Tags are separated by control flow
{template example kind="html<div>"}
  {@param foo: bool}
  {if $foo}
    <div>
  {/if}
  {if $foo}
    </div>
  {/if}
{/template}
```

```soy {.bad}
{template baz}
  <div></div>
{/template}

// Template uses a call. Invalid even if the called template returns one element.
{template example kind="html<div>"}
  {call baz /}
{/template}
```

## Calling templates with `{call}`

HTML-tag templates can be called using the traditional `{call}` syntax:

```soy
{template example kind="html<div>"}
  <div></div>
{/template}

{call example}
{/call}
```

## Calling templates with element composition {#element-composition}

HTML-tag templates can also be called using the **element-composition syntax**.
This syntax is based on the fact that HTML-tag templates return exactly one
element. It is less verbose than using `{call}` and enables stronger type
checking of the attributes of the returned element.

In element composition syntax, the template is invoked as a function and printed
inside an element tag (`<>`). This element can either self-close or close with
an empty tag. For example:

```soy
<{example()}></>
```

If an HTML-tag template is declared as a parameter in another template, it can
be passed directly into the print command.

```soy
{template example}
  {@param tpl: () => html<div>}
  <{$tpl}></>
{/template}
```

Element composition can also chain to produce one final element that multiple
templates can collaborate on.

```soy
{template example kind="html<div>"}
  <div></div>
{/template}

{template exampleCaller kind="html<div>"}
  <{example()}></>
{/template}

{template callerCaller}
  <{exampleCaller()}></>
{/template}
```

### Passing values

The following sections discuss three options in element composition syntax for
passing values to an HTML-tag template:

*   Pass parameters as function arguments.
*   Pass `html` parameters as slots.
*   Pass attributes as HTML attributes

#### Pass parameters as function arguments {#param}

To pass parameter values to an HTML-tag template, pass them as key-value pairs
in the function call.

```soy
{template example kind="html<div>"}
  {@param name:string}
  <div>{$name}</div>
{/template}

{template exampleCaller}
  <{example(name: 'Bob')}></>
{/template}
```

When printing an HTML-tag template passed as a parameter, all required
parameters must be provided with the
[bind()](template-types.md#how-do-you-partially-bind-a-template) function.

```soy
{template example}
  {@param tpl: (a:number) => html<div>}
  <{$tpl.bind(record(a:3))}></>
{/template}
```

#### Pass `html` parameters as `<parameter>`. {#param}

Parameters of type `html` can be passed in as a `<parameter>` node.

```soy
{template example kind="html<div>"}
  {@param myContent:html}
  <div>{$myContent}</div>
{/template}

{template exampleCaller}
  <{example()}>
    <parameter slot="myContent">
      Hello
    </parameter>
  </>
{/template}
```

If a template only contains one `html` parameter, it can omit the `<parameter>`
tag.

```soy
{template exampleCaller}
  <{example()}>Hello</>
{/template}
```

#### Pass attributes as HTML attributes {#attribute}

HTML-tag templates can declare specific attributes that it requires using the
`@attribute` parameter command.

```soy
{template example kind="html<div>"}
  {@attribute aria-label}
  <div @aria-label></div>
{/template}
```

A caller can then pass this attribute through an HTML attribute on the element
composition call.

```soy
{template caller kind="html<div>"}
  <{example()} aria-label="SomeLabel"></>
{/template}
```

In a set of chaining template calls, use `@` in intermediate HTML-tag templates
to pass the attribute:

```soy
{template caller kind="html<div>"}
  {@attribute aria-label:string}
  <{example()} @aria-label></>
{/template}
```

This attribute parameter can be represented in template types using the "@"
prefix.

```soy
{template example}
  {@param tpl: (@aria-label:string) => html<div>}
{/template}
```

An @attribute parameter, if marked as optional, will omit the attribute on the
final DOM output if it is not passed in. The following will produce an empty
`div`.

```soy
{template example kind="html<div>"}
  {@attribute? aria-label}
  <div @aria-label></div>
{/template}

{template caller kind="html<div>"}
  <{example()}></>
{/template}
```

You can specify a default in the callee HTML-tag template.

```soy
{template example kind="html<div>"}
  {@attribute? aria-label:string}
  <div @aria-label="SomeDefault"></div>
{/template}
```

#### Attribute Concatenation {#attribute-concat}

When callers pass values for the `style`, `class`, `jsdata`, `jsaction`, and
`jsmodel` attributes, the passed-in values are concatenated to any existing
values. For example, the output of the following is `<div class="Foo
Bar"></div>`.

```soy
{template example kind="html<div>"}
  {@attribute? class: string}
  <div @class="Foo"></div>
{/template}

{template caller kind="html<div>"}
  <{example()} class="Bar"></>
{/template}
```


#### Arbitrary attributes {#attributeStar}

HTML-tag templates can declare that they accept all attributes except attributes
already present. This is done using an `{@attribute *}` parameter.

```soy
{template example kind="html<div>"}
  {@attribute *}
  <div class="Bar"></div>
{/template}
```

In the above example, a template would be able to pass along all attributes
except `class`.

```soy
{template caller kind="html<div>"}
  <{example()} data-foo="3"></>
{/template}
```

#### Incremental DOM {#incrementaldom}

Element composition shares many of the properties that `calls` do.

Keys can be represented using the `key` command.

```soy
{template a}
  <{foo()} {key 0}></>
{/template}
```

`{skip}` does not work on element composition calls.

```soy {.bad}
{template a}
  <{foo()} {skip}></> // not allowed
{/template}
```

In a Soy element, you can use element composition to call a Soy template. You
cannot use element composition to call another Soy element.

```soy

{template base kind="html<?>"}
  <div></div>
{/template}

{element a kind="html<?>"}
  <{base()} /> // This is allowed
{/element}

{element b}
  <{a()} /> // This is not
{/element}
```
