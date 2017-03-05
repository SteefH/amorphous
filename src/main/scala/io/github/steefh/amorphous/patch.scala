package io.github.steefh.amorphous

import scala.annotation.implicitNotFound

package object patch {
  import shapeless._
  import labelled._
  import record._
  import ops.record._

  @implicitNotFound("Cannot patch a field of type ${Value} with a field of type ${ValuePatch}")
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

  implicit def patchMapWithMapOfOptions[K, V]: FieldPatcher[Map[K, V], Map[K, Option[V]]] =
    FieldPatcher { (e, u) => u.foldLeft(e) {
      case (acc, (k, Some(v))) => acc + (k -> v)
      case (acc, (k, None)) => acc - k
    }}


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

  implicit def patchProductWithPatchOption[Target <: Product, Patch <: Product, TargetRepr <: HList, PatchRepr <: HList](
      implicit
      sourceGen: LabelledGeneric.Aux[Target, TargetRepr],
      patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
      patcher: FieldSubsetPatcher[TargetRepr, PatchRepr]
  ): FieldPatcher[Target, Option[Patch]] =
    FieldPatcher {
      case (e, Some(u)) => e patchedWith u
      case (e, None) => e
    }
  implicit def patchProductWithPatch[Target <: Product, Patch <: Product, TargetRepr <: HList, PatchRepr <: HList](
      implicit
      sourceGen: LabelledGeneric.Aux[Target, TargetRepr],
      patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
      patcher: FieldSubsetPatcher[TargetRepr, PatchRepr]
  ): FieldPatcher[Target, Patch] = FieldPatcher {
    (e, u) => e patchedWith u
  }

  implicit def patchProductWithProduct[Target <: Product]: FieldPatcher[Target, Target] =
    FieldPatcher {
      (_, u) => u
    }

  implicit def patchProductWithProductOption[Target <: Product]: FieldPatcher[Target, Option[Target]] =
    FieldPatcher {
      case (_, Some(u)) => u
      case (e, None) => e
    }

  implicit def patchProductOptionWithProductOption[Target <: Product]: FieldPatcher[Option[Target], Option[Target]] =
    FieldPatcher {
      case (e, None) => e
      case (_, u) => u
    }

  trait FieldSubsetPatcher[TargetRepr <: HList, PatchRepr <: HList] {
    def apply(source: TargetRepr, patch: PatchRepr): TargetRepr
  }

  implicit def deriveHNilPatcher[TargetRepr <: HList]: FieldSubsetPatcher[TargetRepr, HNil] =
    new FieldSubsetPatcher[TargetRepr, HNil] {
      override def apply(source: TargetRepr, patch: HNil): TargetRepr = source
    }

  @implicitNotFound("Cannot patch a field of type ${V} with a field of type ${P}")
  trait FieldPatchedWithOp[V, P] {
    def apply(v: V, p: P): V
  }


  object FieldPatchedWithOp {
    def apply[V, P](implicit patcher: FieldPatcher[V, P]): FieldPatchedWithOp[V, P] = new FieldPatchedWithOp[V, P] {
      override def apply(v: V, p: P): V = patcher(v, p)
    }
  }

  implicit class FieldPatchedWithSyntax[V](val v: V) extends AnyVal {
    def fieldPatchedWith[P](p: P)(implicit op: FieldPatchedWithOp[V, P]): V = op(v, p)
  }

  implicit def deriveHListPatcher[TargetRepr <: HList, Value, Key <: Symbol, PatchValue, Tail <: HList](
      implicit
      key: Witness.Aux[Key],
      tailPatcher: FieldSubsetPatcher[TargetRepr, Tail],
      selector: Selector.Aux[TargetRepr, Key, Value],
      valuePatcher: FieldPatcher[Value, PatchValue],
//      valuePatcher: FieldPatchedWithOp[Value, PatchValue],
      updater: Updater.Aux[TargetRepr, FieldType[Key, Value], TargetRepr]
  ): FieldSubsetPatcher[TargetRepr, FieldType[Key, PatchValue] :: Tail] =
    new FieldSubsetPatcher[TargetRepr, FieldType[Key, PatchValue] :: Tail] {
      override def apply(sourceRepr: TargetRepr, patch: FieldType[Key, PatchValue] :: Tail): TargetRepr = {
        val patchedTail = tailPatcher(sourceRepr, patch.tail)
        val sourceValue = selector(patchedTail)
        val patchedValue = valuePatcher(sourceValue, patch.head)
        patchedTail.replace(key, patchedValue)
      }
    }

  @implicitNotFound("Type ${Target} cannot be patched by type ${Patch}")
  trait PatchedWithOp[Target, Patch] {
    def apply(target: Target, patch: Patch): Target
  }

  object PatchedWithOp {
    implicit def patchedWithOp[Target, TargetRepr <: HList, Patch <: Product, PatchRepr <: HList](
        implicit
        sourceGen: LabelledGeneric.Aux[Target, TargetRepr],
        patchGen: LabelledGeneric.Aux[Patch, PatchRepr],
        patcher: FieldSubsetPatcher[TargetRepr, PatchRepr]
    ): PatchedWithOp[Target, Patch] = new PatchedWithOp[Target, Patch] {
      def apply(target: Target, patch: Patch): Target =
        sourceGen from patcher(sourceGen to target, patchGen to patch)
    }
  }

  implicit class PatchedWithSyntax[Target <: Product](val target: Target) extends AnyVal {
    def patchedWith[Patch <: Product](patch: Patch)(
        implicit
        patchedWithOp: PatchedWithOp[Target, Patch]
    ): Target = patchedWithOp(target, patch)
  }
}
