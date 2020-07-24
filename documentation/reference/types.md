# Types


Soy supports a basic type system. Parameters can be strictly typed to:

*   enable compile time type checking of template calls
*   allow access to protocol buffer fields
*   generate smaller/faster code

[TOC]

## Primitive types

### `any` {#any}

The any type can only be printed, or coerced to a string.

### `?` {#unknown}

The unknown type disables type checking. It is useful if you don't care, or are
migrating templates from not using types to using types (this way you can
migrate one parameter at a time).

### `null` {#null}

The `null` type is not very useful on its own, but can be as part of [composite
types](#composite).

Backend    | type in host language
---------- | ----------------------------------------------------------
JavaScript | `null`, `undefined`
SoySauce   | `com.google.template.soy.data.restricted.NullData`, `null`
Tofu       | `com.google.template.soy.data.restricted.NullData`
Python     | `NoneType`

### `bool` {#bool}

Takes on a boolean value.

Backend    | type in host language
---------- | ----------------------------------------------------------------
JavaScript | `boolean`
SoySauce   | `boolean`, `com.google.template.soy.data.restricted.BooleanData`
Tofu       | `com.google.template.soy.data.restricted.BooleanData`
Python     | `bool`

### `int` {#int}

An integer. Due to limitations of JavaScript this type is only guaranteed to
have 52 bit of precision.

Backend    | type in host language
---------- | ----------------------------------------------------------
JavaScript | `number`
SoySauce   | `long`, `com.google.template.soy.data.restricted.LongData`
Tofu       | `com.google.template.soy.data.restricted.LongData`
Python     | `long`

### `float` {#float}

A floating point number with 64 bits of precision.

Backend    | type in host language
---------- | ---------------------
JavaScript | `number`
SoySauce   | `double`, `FloatData`
Tofu       | `FloatData`
Python     | `float`

### `number` {#number}

An alias for `int|float`. (Technically a [composite type](#union), not a
primitive.)

### `string` {#string}

`string` is one of the most common types in Soy. In addition to plain strings
there are a number of safe subtypes.


<table>
<thead>
<tr>
<th>Backend</th>
<th>type in host language</th>
</tr>
</thead>

<tbody>
<tr>
<td>JavaScript
</td>
<td><code>string</code>, <code>goog.soy.data.UnsanitizedText</code>, (all safe
string subtypes)</td>
</tr>
<tr>
<td>SoySauce

</td>
<td><code>string</code>,
<code>com.google.template.soy.data.restricted.StringData</code>,
<code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Tofu
</td>
<td><code>com.google.template.soy.data.restricted.StringData</code>,
<code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Python
</td>
<td><code>string</code>, <code>UnsanitizedText</code> (all safe string
subtypes)</td>
</tr>
</tbody>
</table>

### `html` {#html}

`html` is for a string that contains safe HTML content. Safe html is HTML that
comes from a trusted source, typically another Soy template.

Backend    | type in host language
---------- | ---------------------------------------------------------
JavaScript | `goog.soy.data.SanitizedHtml`
SoySauce   | `string`, `com.google.template.soy.data.SanitizedContent`
Tofu       | `com.google.template.soy.data.SanitizedContent`
Python     | `sanitize.SanitizedHtml`, `html_types.SafeHtml`


### `js` {#js}

`js` is for a string that contains safe JavaScript code. Safe JavaScript is a
string that comes from a trusted source, typically another Soy template.

Backend    | type in host language
---------- | ---------------------------------------------------------
JavaScript | `goog.soy.data.SanitizedJs`, `goog.html.SafeScript`
SoySauce   | `string`, `com.google.template.soy.data.SanitizedContent`
Tofu       | `com.google.template.soy.data.SanitizedContent`
Python     | `sanitize.SanitizedJs`, `html_types.SafeScript`


### `uri` {#uri}

`uri` is for a string that contains a URI that came from a trusted source.


<table>
<thead>
<tr>
<th>Backend</th>
<th>type in host language</th>
</tr>
</thead>

<tbody>
<tr>
<td>JavaScript

</td>
<td><code>goog.soy.data.SanitizedUri</code>, <code>goog.Uri</code>,
<code>goog.html.SafeUrl</code>,
<code>goog.html.TrustedResourceUrl</code></td>
</tr>
<tr>
<td>SoySauce
</td>
<td><code>string</code>,
<code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Tofu</td>
<td><code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Python</td>
<td><code>sanitize.SanitizedUri</code></td>
</tr>
</tbody>
</table>

Additionally, all backends have support for coercing
`webutil.html.types.SafeUrlProto` to a `uri` object.

### `trusted_resource_uri` {#trusted_resource_uri}

`trusted_resource_uri` is for a string that contains a URI that came from a
trusted source and additionally can be used as a Script `src` or in other
sensitive contexts.
<table>
<thead>
<tr>
<th>Backend</th>
<th>type in host language</th>
</tr>
</thead>

<tbody>
<tr>
<td>JavaScript
</td>
<td><code>goog.soy.data.SanitizedTrustedResourceUri</code>,
<code>goog.html.TrustedResourceUrl</code></td>
</tr>
<tr>
<td>SoySauce
</td>
<td><code>string</code>,
<code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Tofu</td>
<td><code>com.google.template.soy.data.SanitizedContent</code></td>
</tr>
<tr>
<td>Python</td>
<td><code>sanitize.TrustedResourceUri</code></td>
</tr>
</tbody>
</table>

Additionally, all backends have support for coercing
`webutil.html.types.TrustedResourceUrlProto` to a `trusted_resource_uri` object.

### `attributes` {#attributes}

`attributes` is for a string that contains 0 or more HTML attribute value pairs.

Backend    | type in host language
---------- | ---------------------------------------------------------
JavaScript | `goog.soy.data.SanitizedHtmlAttribute`
SoySauce   | `string`, `com.google.template.soy.data.SanitizedContent`
Tofu       | `com.google.template.soy.data.SanitizedContent`
Python     | `sanitize.SanitizedHtmlAttribute`

Unlike the other safe string types there is no equivalent for attributes.

### `css` {#css}

`css` is for a string that contains CSS that comes from a trusted source.

NOTE: currently css contains either a set of CSS rules or a set of css
attributes and the compiler does not make any distinction. This will likely
change in the future.

Backend    | type in host language
---------- | ---------------------------------------------------------
JavaScript | `goog.soy.data.SanitizedCss`, `goog.html.SafeStyle`
SoySauce   | `string`, `com.google.template.soy.data.SanitizedContent`
Tofu       | `com.google.template.soy.data.SanitizedContent`
Python     | `sanitize.SanitizedCss`, `html_types.SafeStyleSheet`

Additionally, all backends have support for coercing
`webutil.html.types.SafeStyleSheetProto` and `webutil.html.types.SafeStyleProto`
to a `css` object.


### `template` {#template}

The `template` type represents a Soy template or element. Templates may be
instantiated using the `template()` built-in. Only basic templates or elements
may be created in expressions; deltemplates are not allowed. Additionally, for
HTML templates, strict HTML is required for templates used in expressions.

Parameters may be bound to template-type expressions using the
[`.bind()`](functions.md#bind) method.

Template types may be invoked using the normal `{call}` syntax, with any unset
parameters passed as parameters.

Template type declarations consist of a list of named parameters, their
corresponding types, and the return type of the template.

For example:

```soy
{template .foo}
  {@param tpl: (count: int, greeting: string) => html}

  {call $tpl}
    {param count: 5 /}
    {param greeting: 'Hello!' /}
  {/call}
{/template}
```

## Composite types {#composite}

### Union types: `A|B` {#union}

An `A|B` union type can contain either a value of type `A` or a value of type
`B`.

### `list<T>` {#list}

A list can contain any type as an element. Lists can be accessed using the
[indexed operators](expressions.md#indexing-operators).

For example,

```soy
{template .foo}
  {@param l: list<string>}
  {$l[0]}, {$l[1]}, {$l[2]}
{/template}
```

The most common thing to do with a list is to iterate over it, using the for
loop:

```soy
{template .foo}
  {@param l: list<string>}
  <ul>
  {for $el in $l}
    <li>{$el}
  {/for}
  </ul>
{/template}
```

Backend    | type in host language
---------- | --------------------------------------------------------
JavaScript | `Array`
SoySauce   | `java.util.List`, `com.google.template.soy.data.SoyList`
Tofu       | `com.google.template.soy.data.SoyList`
Python     | `list`

### `map<K, V>` {#map}

A map takes two parameters for the key and value types. Maps are accessed using
the [indexed operators](expressions.md#indexing-operators).

For example,

```soy
{template .foo}
  {@param m: map<int, string>}
  <ul>
    {for $key in mapKeys($m)}
      <li>{$key}: {$m[$key]}</li>
      <li>{$key} * 2 = {$key * 2}</li> // arithmetic on numeric keys in map
    {/for}
  </ul>
{/template}
```

Backend    | type in host language
---------- | ---------------------
JavaScript | `soy.map` (which is a structural interface covering ES6 [`Map`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map) and [`jspb.Map`](https://github.com/protocolbuffers/protobuf/blob/master/js/map.js), the most common implementations)
SoySauce   | `java.util.Map`
Tofu       | `java.util.Map`
Python     | `dict`

### `legacy_object_map<K, V>` {#legacy_object_map}

WARNING: Use `map` instead of `legacy_object_map` for new Soy code. See
go/soy-map-faq#advantages for details.

A legacy object map takes two parameters for the key and value types. Legacy
object maps are accessed using the
[indexed operators](expressions.md#indexing-operators).

For example,

```soy
{template .foo}
  {@param m: legacy_object_map<string, string>}
  <ul>
    {for $key in keys($m)}
      <li>{$key}: {$m[$key]}</li>
    {/for}
  </ul>
{/template}
```

NOTE: only `string` is well supported for key types. The behavior for key types
other than string is undefined.

Maps and legacy object maps are distinct types. You cannot pass a legacy object
map to a template expecting a map, and vice versa. If you need to convert from
one map type to the other, use the
[mapToLegacyObjectMap](functions.md#mapToLegacyObjectMap) and
[legacyObjectMapToMap](functions.md#legacyObjectMapToMap) functions.

Backend    | type in host language
---------- | ------------------------------------------------------------------
JavaScript | `Object`
SoySauce   | `java.util.Map`, `com.google.template.soy.data.SoyLegacyObjectMap`
Tofu       | `java.util.Map`, `com.google.template.soy.data.SoyLegacyObjectMap`
Python     | not supported

### records: `[<prop-name>: <prop-type>,...]` {#record}

Record types define an object with a given set of properties. The properties can
be accessed using the [data access operators](expressions.md#data-access)

For example,

```soy
{template .foo}
  {@param person: [age: int, name: string]}
  Name: {$person.name}
  Age: {$person.age}
{/template}
```

In many cases, defining a protocol buffer is superior to using records since it
is less verbose

Backend    | type in host language
---------- | ---------------------------------------------------------
JavaScript | `Object`[^1]
SoySauce   | `java.util.Map`, `com.google.template.soy.data.SoyRecord`
Tofu       | `com.google.template.soy.data.SoyRecord`
Python     | `dict`

### `Message` {#message}

The `Message` type is the generic base class of all protos. This is mostly
useful for Soy plugins which are able to use platform specific generic proto
features.

Backend    | type in host language
---------- | --------------------------------------------
JavaScript | `jspb.Message`
SoySauce   | `com.google.protobuf.Message`
Tofu       | `com.google.template.soy.data.SoyProtoValue`
Python     | unsupported

See the [dev guide](../dev/protos.md) for more information on how protos work.

### protos: `foo.bar.BazProto` {#proto}

Protocol buffers are supported in Soy. They can be accessed as though they were
`record` types with the `.` operator.

Protocol Buffers in Soy have the same semantics as `JSPB`, not `Java` protos.

See the [dev guide](../dev/protos.md) for more information on how protos work.

NOTE: currently, protos are _not supported_ in the Python backend.

<br>

[^1]: The difference between map and record for JavaScript is simply whether the
    keys are strings or symbols.
