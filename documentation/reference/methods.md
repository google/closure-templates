# Methods


Soy methods are called from within Soy [expressions](expressions.md).

The table below lists Soy methods that are available.

[TOC]

## `proto.getExtension(name)`

Returns the value of the extension field of the `proto`, given the name of an
imported extension field as the parameter.

### Example usage

```proto
package soy.example;
message Person {
  extensions 1000 to max;
}
message Height {
  extend Person {
    optional Height height = 1001;
    repeated Height past_height = 1002;
  }
  optional int32 cm = 1;
}
```

The template below accesses the `Height` extension of the `Person` proto.

```soy
{template .foo}
  {@param proto: soy.example.Person}
  {$proto.getExtension(soy.example.Height.height).cm}
{/template}
```

### Repeated extension fields

Access repeated extension fields by appending `List` to the fully qualified name
of the extension.

For example, given the [above definition](#example-usage) of the repeated proto
field `past_height`, it would be accessed as follows:

```soy
{template .heightHistory}
  {@param person: soy.example.Person}
  {for $height in $person.getExtension(soy.example.pastHeightList)}
    {$height.cm}
  {/for}
{/template}
```
