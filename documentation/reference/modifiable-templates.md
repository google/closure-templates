# Modifiable templates

[TOC]

## Basic structure

A `modifiable` template is a template that can have any number of `modifies`
templates associated with it. A `modifiable` template is called like an ordinary
template, but at runtime one of its `modifies` templates can be rendered
instead.

A `modifiable` template will have the `modifiable="true"` attribute. A template
can be associated with it by having the template's name as the `modifies`
attribute. At runtime, if a `modifies` template is in a file with a `{modname}`
declaration that matches an active mod, it will be rendered.

`my/project/feature.soy`

```soy
{namespace my.feature}

{template featureTemplate modifiable="true"}
  A feature
{/template}
```

`my/project/experiment_arm.soy`

```soy
{modname experimentArm}
{namespace my.experiment_arm}

import {featureTemplate} from 'my/project/feature.soy';

{template featureTemplateMod visibility="private" modifies="featureTemplate"}
  A feature with blue text
{/template}
```

`my/project/main.soy`

```soy
{namespace my.app}
import {featureTemplate} from 'my/project/feature.soy';

{template main}
  // If "experimentArm" is active, "A feature with blue text" will be rendered.
  {call featureTemplate /}
{/template}
```

Constraints:

*   A template with `modifiable` must be public and in a file without
    `{modname}`.
*   A template with `modifies` must be private and either in a file with
    `{modname}` or use the `variant` attribute.
*   All modifies templates must have signatures that are compatible with the
    modifiable template.
*   Calls can only be made to the modifiable template.
*   A single Soy file can only modify templates from a single external namespace
    (ie, a single file).

## Variants

The `usevarianttype` attribute declares the type that should be used for the
variant attributes on both `modifies` templates and calls. In calls, if a
`modifies` template with matching `variant` is found, it will be rendered.
Otherwise by default `modifiable` template will render.

```soy
{template withVariants modifiable="true" usevarianttype="string"}
  Something
{/template}

{template withVariantsFoo visibility="private" modifies="withVariants" variant="'foo'"}
  Foo
{/template}

// Compilation error, incorrect variant type.
{template badVariant visibility="private" modifies="withVariants" variant="42"}
{/template}

{template main}
  // Renders "Something"
  {call withVariants /}

  // Renders "Foo"
  {call withVariants variant="'foo'" /}

  // Renders "Something"
  {call withVariants variant="'unknown'" /}

  // Compilation error, incorrect variant type.
  {call withVariants variant="100" /}
{/template}
```

Valid types for `usevarianttype` are `string`, `int`, or any proto enum type.

When calling a modifiable template from Javascript, there will be an extra
parameter `opt_variant` that can be used to pass the variant.

Passing the variant using the Java API is not currently supported. Instead, use
a wrapper template:

```
{template myModifiableWrapper}
  {@param variant: string}
  {call myModifiable variant="$variant" /}
{/template}

{template myModifiable modifiable="true" usevarianttype="string"}
{/template}
```

## legacydeltemplatenamespace

To ease migration, the `legacydeltemplatenamespace` can be used to incrementally
migrate your templates. A `modifiable` template with
`legacydeltemplatenamespace` can interoperate with `deltemplates` and
`delcalls`.

```soy
{template legacy modifiable="true" legacydeltemplatenamespace="my.legacy.name" usevarianttype="string"}
{/template}
```

These are valid:

```soy
{modname someMod}

{deltemplate my.legacy.name}
{/deltemplate}

{deltemplate my.legacy.name variant="'foo'"}
{/deltemplate}

{delcall my.legacy.name}
{/delcall}
```
