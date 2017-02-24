package io.github.steefh.amorphous

import scala.annotation.implicitNotFound


package object extract {

  import shapeless.labelled.FieldType
  import shapeless.ops.record.{Keys, Selector}
  import shapeless.{HList, ::, HNil, LabelledGeneric}

  trait Extractor[SourceRepr, TargetKeys] {
    type Out

    def apply(s: SourceRepr, t: TargetKeys): Out
  }


  object Extractor {
    type Aux[S, T, O] =
      Extractor[S, T] {type Out = O}

    implicit def hnilExtractor[SourceRepr]: Aux[SourceRepr, HNil, HNil] =
      new Extractor[SourceRepr, HNil] {
        type Out = HNil

        def apply(s: SourceRepr, t: HNil): HNil = HNil
      }

    implicit def hlistExtractor[SourceRepr <: HList, KeysTail <: HList, FieldsTail <: HList, Key, Value](
        implicit
        tailExtractor: Aux[SourceRepr, KeysTail, FieldsTail],
        selector: Selector.Aux[SourceRepr, Key, Value]
    ): Aux[SourceRepr, Key :: KeysTail, FieldType[Key, Value] :: FieldsTail] =
      new Extractor[SourceRepr, Key :: KeysTail] {
        type Out = FieldType[Key, Value] :: FieldsTail

        override def apply(s: SourceRepr, t: Key :: KeysTail): FieldType[Key, Value] :: FieldsTail = {
          shapeless.labelled.field[Key](selector(s)) :: tailExtractor(s, t.tail)
        }
      }
  }

  @implicitNotFound("Type ${Target} cannot be extracted from ${Source}")
  trait ExtractedToOp[Source, Target] {
    def apply(v: Source): Target
  }

  object ExtractedToOp {
    implicit def extractorOp[Source, Target, SourceRepr <: HList, TargetKeys <: HList, TargetRepr <: HList](
      implicit
      sourceGen: LabelledGeneric.Aux[Source, SourceRepr],
      targetGen: LabelledGeneric.Aux[Target, TargetRepr],
      keys: Keys.Aux[TargetRepr, TargetKeys],
      extractor: Extractor.Aux[SourceRepr, TargetKeys, TargetRepr]
    ): ExtractedToOp[Source, Target] = new ExtractedToOp[Source, Target] {
      override def apply(v: Source): Target =
        targetGen.from(extractor(sourceGen.to(v), keys()))
    }
  }

  implicit class ExtractedToSyntax[Source](val value: Source) extends AnyVal {
    def extractedTo[Target](implicit extractorOp: ExtractedToOp[Source, Target]): Target = {
      extractorOp(value)
    }
  }

}
