package io.github.steefh.amorphous




package object extract {

  import scala.annotation.implicitNotFound
  import shapeless.labelled.FieldType
  import shapeless.ops.record._
  import shapeless.{HList, ::, HNil, LabelledGeneric}

  trait ExtractTo[SourceRepr, TargetKeys] {
    type Out

    def apply(s: SourceRepr, t: TargetKeys): Out
  }


  object ExtractTo {
    type Aux[S, T, O] =
      ExtractTo[S, T] {type Out = O}

    implicit def hnilExtractor[SourceRepr]: Aux[SourceRepr, HNil, HNil] =
      new ExtractTo[SourceRepr, HNil] {
        type Out = HNil

        def apply(s: SourceRepr, t: HNil): HNil = HNil
      }

    implicit def hlistExtractor[SourceRepr <: HList, KeysTail <: HList, FieldsTail <: HList, Key, Value](
        implicit
        tailExtractor: Aux[SourceRepr, KeysTail, FieldsTail],
        selector: Selector.Aux[SourceRepr, Key, Value]
    ): Aux[SourceRepr, Key :: KeysTail, FieldType[Key, Value] :: FieldsTail] =
      new ExtractTo[SourceRepr, Key :: KeysTail] {
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
      extractor: ExtractTo.Aux[SourceRepr, TargetKeys, TargetRepr]
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


  trait ExtractInto[SourceRepr, TargetRepr] {
    def apply(s: SourceRepr, t: TargetRepr): TargetRepr
  }
  object ExtractInto {
    implicit def hnilExtractInto[TargetRepr <: HList]: ExtractInto[HNil, TargetRepr] = new ExtractInto[HNil, TargetRepr] {
      override def apply(s: HNil, t: TargetRepr): TargetRepr = t
    }
    implicit def hlistHeadKeyUnknownExtractInto[K, V, Rest <: HList, TargetRepr <: HList](
        implicit
//        targetKeys: Keys.Aux[TargetRepr, TargetKeys],
        lacksKey: LacksKey[TargetRepr, K],
        tailExtractInto: ExtractInto[Rest, TargetRepr]
    ): ExtractInto[FieldType[K, V] :: Rest, TargetRepr] =
      new ExtractInto[FieldType[K, V] :: Rest, TargetRepr] {
        override def apply(s: FieldType[K, V] :: Rest, t: TargetRepr): TargetRepr = {
          tailExtractInto(s.tail, t)
        }
      }

    implicit def hlistHeadKeyKnownExtractInto[K, V, Rest <: HList, TargetRepr <: HList](
        implicit
        updater: Updater.Aux[TargetRepr, FieldType[K, V], TargetRepr],
        tailExtractInto: ExtractInto[Rest, TargetRepr]
    ): ExtractInto[FieldType[K, V] :: Rest, TargetRepr] =
      new ExtractInto[FieldType[K, V] :: Rest, TargetRepr] {
        override def apply(s: FieldType[K, V] :: Rest, t: TargetRepr): TargetRepr = {
          tailExtractInto(s.tail, updater(t, s.head))
        }
      }
  }



  @implicitNotFound("Cannot extract ${Source} into ${Target}")
  trait ExtractedIntoOp[Source, Target] {
    def apply(s: Source, t: Target): Target
  }

  object ExtractedIntoOp {
    implicit def extractedIntoOp[Source, Target, SourceRepr <: HList, TargetRepr <: HList](
        implicit
        sourceGen: LabelledGeneric.Aux[Source, SourceRepr],
        targetGen: LabelledGeneric.Aux[Target, TargetRepr],
        extractInto: ExtractInto[SourceRepr, TargetRepr]
    ): ExtractedIntoOp[Source, Target] = new ExtractedIntoOp[Source, Target] {
      override def apply(s: Source, t: Target): Target = {
        targetGen.from(extractInto(sourceGen.to(s), targetGen.to(t)))
      }
    }
    def apply[Source, Target](implicit extractedIntoOp: ExtractedIntoOp[Source, Target]): ExtractedIntoOp[Source, Target] = extractedIntoOp
  }

  implicit class ExtractedIntoSyntax[Source](val source: Source) extends AnyVal {
    def extractedInto[Target](target: Target)(implicit extractedIntoOp: ExtractedIntoOp[Source, Target]): Target = {
      extractedIntoOp(source, target)
    }
  }


}
