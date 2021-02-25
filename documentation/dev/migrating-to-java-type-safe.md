

## Advanced Migration strategies

Sometimes when migrating to the type safe API you will come across templates
with complex parameters that are not well supported. Over time we hope to expand
the number of supported usecases in the invocation builder API, but in the mean
time here are some workarounds.

These approaches won't all be the most type safe, but they will allow adopting
the type safe api and provides for type safety improving over time.

### Missing setters due to indirect parameters?

See the guide on
[indirect parameters](java-template-builders.md##indirect-params) for details

Consider

```soy
{template topLevel}
  {call child data="all"/}
{/template}

{template child}
  {@param proto: Foo}
  ...
{/template}
```

In this case it will be impossible to set the `proto` param when calling the
`topLevel` template from `Java`. The workaround is to explicitly redeclare the
parameter at the top level.

```soy
{template topLevel}
  {@param proto: Foo}
  {call child data="all"/}
{/template}
```

This will ensure a setter is generated and can be safely set from Java.

### Missing setters due to complex parameters parameters?

Certain complex record types are not well supported. A good approach would be to
replace these records with `proto` definitions but this might be a lot of work.
As a stepping stone to the type safe api a workaround might be to introduce an
adapter templates

Consider

```soy
{template foo}
  {@param complex: list<list<[foo:string,bar:number]>>}
...
{/template}
```

This type is not supported in the type safe java api and thus no setter will be
generated. As a workaround without rewriting the template you can introduce an
adapter template.

```soy

{template fooAdapter}
  {@param complex: list<list<?>>}
  {call foo data="all"/}
{/template}
```

Now a setter will be generated and you can directly call `fooAdapter` using the
java api.

### Multiple templates sharing the same parameters?

This is a common but complex case.

Consider the following templates,

```soy
{template a}
  {@param p1 :...}
  ...
  {@param pN :...}
{/template}
{template b}
  {@param p1 :...}
  ...
  {@param pN :...}
{/template}
{template c}
  {@param p1 :...}
  ...
  {@param pN :...}
{/template}
```

Here we see 3 different templates with the same set of parameters. In java the
callsite might look like:

```java
soySauce.renderTemplate(calculateTemplateName(...)).setData(getParams(...))
```

Because the logic for preparing parameters is shared it might be very difficult
or tedious to migrate to the type safe api. There are a number of approaches
that could be considered

1.  move dispatch into soy

    Write a dispatcher template

    ```
    {template dispatch}
      {@param selection: string}  // maybe consider using a proto enum?
      {@param p1 :...}
      ...
      {@param pN :...}
      {switch $selection}
        {case 'a'}
          {call a data="all"/}
        {case 'b'}
          {call b data="all"/}
        ...
      {/switch}
    {/template}
    ```

    Now there is a single template that will be called from java and therefore
    migrating to the type safe parameter api should be more straightforward.

1.  use reflective APIs in SoyTemplate

    ```java
    soySauce.newRenderer(
        SoyTemplates.getBuilder(calculateTemplateClass(...))
            .setParam(Template1.PARAM1, ...)
            .setParam(Template1.PARAM2, ...)
            .build());
    ```

    Instead of calculating the fully qualified template name you calculate the
    fully qualified java class name of the SoyTemplate subclass, and pass it to
    `SoyTemplates#getBuilder`. Now you can call `Builder#setParam` once for each
    shared parameter. The first argument to `setParam` is an instance of
    `SoyTemplateParam`. Each `SoyTemplate` subclass has a public static
    `SoyTemplateParam` field for every public param in the template. And as long
    as the `SoyTemplateParam` instances are identical it doesn't matter that you
    pass `Template1.PARAM1` to `Template2.Builder#setParam`.

1.  turn off type checking with `data="..."`

    The logic for building the map of params in java can be maintained by
    writing a tiny adapter template

    For example,

    ```soy
    {template aAdapter}
      {@param params: ?}
      {call a data="$params"/}
    {/template}
    ```

    Then in java you can simply call the adapter
    `AAdapter.builder().setParams(generateMap(...)).build()`

    which should simplify adoption.
