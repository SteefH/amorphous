#`amorphous`

`amorphous` is a Scala library for Scala 2.11.x and 2.12.x built on top of [`shapeless`](https://github.com/milessabin/shapeless) that offers methods to reduce boilerplate in your Scala code.

For now, `amorphous` has a limited API, but in the future it might mature into a feature rich toolkit for working with Scala types

##Features

##`patchedWith`

This problem might be familiar: you have a case class representing some state, and you want to update that state with values from another case class. For instance:

```scala
case class UserData(
  firstName: String,
  lastName: String
) {
  def updatedWith(update: UserDataUpdate): UserData =
	copy(
	  firstName = update.firstName.getOrElse(firstName), // only change if it is set in update
	  lastName = update.lastName.getOrElse(lastName)     // only change if it is set in update
	)
}

// allows for partial updates of UserData
case class UserDataUpdate(
  firstName: Option[String] = None,
  lastName: Option[String] = None
)
```

As the `UserData` case class grows over time, with more fields added, the `UserDataUpdate` case class might also get more fields, resulting in an `updatedWith` method getting bigger and bigger as well. This is boilerplate code that doesn't do much interesting stuff. `patchedWith` aims to relieve you of having to write and maintain code like that. Your code might end up looking like this:

```scala
import io.github.steefh.amorphous.patch._

case class UserData(
  firstName: String,
  lastName: String
) {
  def updatedWith(update: UserDataUpdate): UserData =
	this patchedWith update
}

// allows for partial updates of UserData
case class UserDataUpdate(
  firstName: Option[String] = None,
  lastName: Option[String] = None
)
```

`patchedWith` allows you to "patch" a case class instance with an instance of another, unrelated, case class, meaning that you create a new instance with field values from the original instance, replaced with field values from the patching instance in case field names are the same. An example taken from a Scala REPL session might illustrate this:

```scala
scala> import io.github.steefh.amorphous.patch._
import io.github.steefh.amorphous.patch._

scala> case class Obj(intValue: Int, stringValue: String)
defined class Obj

scala> case class Patch(intValue: Int)
defined class Patch

scala> val patched = Obj(1, "a") patchedWith Patch(2)
patched: Obj = Obj(2,a)
```

This also works when the patching type has a field with the same name as in the type being patched, where the patching field's type is an `Option` of the type of the field being patched. When the patching object has its field's value set to `Some(x)`, the result will contain `x` in the patched field. When the patching field's value is `None`, the patched field will be left untouched. 

```scala
scala> case class PatchWithOption(intValue: Option[Int])
defined class PatchWithOption

scala> val patched = Obj(1, "a") patchedWith PatchWithOption(Some(2))
patched: Obj = Obj(2,a)

scala> val patched = Obj(1, "a") patchedWith PatchWithOption(None)
patched: Obj = Obj(1,a)
```

The patching type cannot contain fields that are not in the type being patched:

```scala
scala> case class WithUnknownField(unknownField: String)
defined class WithUnknownField

scala> val patched = Obj(1, "a") patchedWith WithUnknownField("g")
<console>:18: error: No FieldSubsetPatcher for SourceRepr with PatchRepr
       val patched = Obj(1, "a") patchedWith WithUnknownField("g")
```

The patching type's fields should be of the same type as the fields in the type being patched, or an `Option` of the field type in the type being patched:

```scala
scala> case class WithOtherFieldType(intValue: String)
defined class WithOtherFieldType

scala> val patched = Obj(1, "a") patchedWith WithOtherFieldType("g")
<console>:18: error: No FieldSubsetPatcher for SourceRepr with PatchRepr
       val patched = Obj(1, "a") patchedWith WithOtherFieldType("g")
```

### Nested objects

`patchedWith` also takes care of patching nested objects (continuing in the same REPL session):

```scala
scala> case class WithNestedObj(obj: Obj)
defined class WithNestedObj

scala> case class WithNestedPatch(obj: Patch)
defined class WithNestedPatch

scala> val patched = WithNestedObj(Obj(1, "a")) patchedWith WithNestedPatch(Patch(2))
patched: WithNestedObj = WithNestedObj(Obj(2,a))
```

And for nested patches inside an `Option`:

```scala
scala> case class WithNestedPatchOption(obj: Option[Patch])
defined class WithNestedPatchOption

scala> val patched = WithNestedObj(Obj(1, "a")) patchedWith WithNestedPatchOption(Some(Patch(2)))
patched: WithNestedObj = WithNestedObj(Obj(2,a))

scala> val patched = WithNestedObj(Obj(1, "a")) patchedWith WithNestedPatchOption(None)
patched: WithNestedObj = WithNestedObj(Obj(1,a))
```

And ultimately, for nested objects inside an `Option` patched with a nested patch inside an `Option`:

```scala
scala> case class WithNestedObjOption(obj: Option[Obj])
defined class WithNestedObjOption

scala> val patched = WithNestedObjOption(None) patchedWith WithNestedPatchOption(Some(Patch(2)))
patched: WithNestedObjOption = WithNestedObjOption(None)

scala> val patched = WithNestedObjOption(None) patchedWith WithNestedPatchOption(None)
patched: WithNestedObjOption = WithNestedObjOption(None)

scala> val patched = WithNestedObjOption(Some(Obj(1, "a"))) patchedWith WithNestedPatchOption(Some(Patch(2)))
patched: WithNestedObjOption = WithNestedObjOption(Some(Obj(2,a)))

scala> val patched = WithNestedObjOption(Some(Obj(1, "a"))) patchedWith WithNestedPatchOption(None)
patched: WithNestedObjOption = WithNestedObjOption(Some(Obj(1,a)))
```
