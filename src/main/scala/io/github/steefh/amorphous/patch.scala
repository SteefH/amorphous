package io.github.steefh.amorphous

package object patch {
  import shapeless._
  import labelled._
  import record._
  import ops.record._

  @annotation.implicitNotFound(msg = "No field patch for ${Value} with ${ValuePatch}")
  trait FieldPatcher[Value, ValuePatch] {
    def apply(value: Value, patch: ValuePatch): Value
  }

  object FieldPatcher {
    def apply[Value, ValuePatch](patchFunction: (Value, ValuePatch) => Value) =
      new FieldPatcher[Value, ValuePatch] {
        override def apply(value: Value, patch: ValuePatch): Value = patchFunction(value, patch)
      }
  }

  implicit def simplePatch[Value]: FieldPatcher[Value, Value] =
    FieldPatcher { (_, v) => v }

  implicit def patchValueWithValueOption[Value]: FieldPatcher[Value, Option[Value]] =
    FieldPatcher { (v, p) => p getOrElse v }

  implicit def patchValueOptionWithValueOption[Value]: FieldPatcher[Option[Value], Option[Value]] =
    FieldPatcher { (e, u) => u orElse e }

  implicit def patchProductOptionWithPatchOption[Obj <: Product, Patch <: Product, ObjRepr <: HList, PatchRepr <: HList](
    implicit
    sourceGen: LabelledGeneric.Aux[Obj, ObjRepr],
    patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
    patcher: FieldSubsetPatcher[ObjRepr, PatchRepr]
  ): FieldPatcher[Option[Obj], Option[Patch]] =
    FieldPatcher {
      case (Some(e), Some(u)) => Some(e patchedWith u)
      case (Some(e), None) => Some(e)
      case (None, _) => None
    }

  implicit def patchProductWithPatchOption[Obj <: Product, Patch <: Product, ObjRepr <: HList, PatchRepr <: HList](
      implicit
      sourceGen: LabelledGeneric.Aux[Obj, ObjRepr],
      patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
      patcher: FieldSubsetPatcher[ObjRepr, PatchRepr]
  ): FieldPatcher[Obj, Option[Patch]] =
    FieldPatcher {
      case (e, Some(u)) => e patchedWith u
      case (e, None) => e
    }
  implicit def patchProductWithPatch[Obj <: Product, Patch <: Product, ObjRepr <: HList, PatchRepr <: HList](
      implicit
      sourceGen: LabelledGeneric.Aux[Obj, ObjRepr],
      patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
      patcher: FieldSubsetPatcher[ObjRepr, PatchRepr]
  ): FieldPatcher[Obj, Patch] = FieldPatcher {
    (e, u) => e patchedWith u
  }

  implicit def patchProductWithProduct[Obj <: Product]: FieldPatcher[Obj, Obj] =
    FieldPatcher {
      (_, u) => u
    }

  implicit def patchProductWithProductOption[Obj <: Product]: FieldPatcher[Obj, Option[Obj]] =
    FieldPatcher {
      case (_, Some(u)) => u
      case (e, None) => e
    }

  implicit def patchProductOptionWithProductOption[Obj <: Product]: FieldPatcher[Option[Obj], Option[Obj]] =
    FieldPatcher {
      case (e, None) => e
      case (_, u) => u
    }

  @annotation.implicitNotFound(msg = "No FieldSubsetPatcher for ${SourceRepr} with ${PatchRepr}")
  trait FieldSubsetPatcher[SourceRepr <: HList, PatchRepr <: HList] {
    def apply(source: SourceRepr, patch: PatchRepr): SourceRepr
  }

  implicit def deriveHNilPatcher[SourceRepr <: HList]: FieldSubsetPatcher[SourceRepr, HNil] =
    new FieldSubsetPatcher[SourceRepr, HNil] {
      override def apply(source: SourceRepr, patch: HNil): SourceRepr = source
    }

  implicit def deriveHListPatcher[SourceRepr <: HList, Value, Key <: Symbol, PatchValue, Tail <: HList](
      implicit
      key: Witness.Aux[Key],
      tailPatcher: FieldSubsetPatcher[SourceRepr, Tail],
      selector: Selector.Aux[SourceRepr, Key, Value],
      valuePatcher: FieldPatcher[Value, PatchValue],
      updater: Updater.Aux[SourceRepr, FieldType[Key, Value], SourceRepr]
  ): FieldSubsetPatcher[SourceRepr, FieldType[Key, PatchValue] :: Tail] =
    new FieldSubsetPatcher[SourceRepr, FieldType[Key, PatchValue] :: Tail] {
      override def apply(sourceRepr: SourceRepr, patch: FieldType[Key, PatchValue] :: Tail): SourceRepr = {
        val patchedTail = tailPatcher(sourceRepr, patch.tail)
        val sourceValue = selector(patchedTail)
        val patchedValue = valuePatcher(sourceValue, patch.head)
        patchedTail.replace(key, patchedValue)
      }
    }


  implicit class PatchSyntax[Source <: Product](val source: Source) extends AnyVal {
    def patchedWith[Patch <: Product, SourceRepr <: HList, PatchRepr <: HList](patch: Patch)(
        implicit
        sourceGen: LabelledGeneric.Aux[Source, SourceRepr],
        patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
        patcher: FieldSubsetPatcher[SourceRepr, PatchRepr]
    ): Source = sourceGen from patcher(sourceGen to source, patchGen to patch)
  }
}
