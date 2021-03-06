package io.github.steefh.amorphous

import scala.reflect.macros.blackbox


package object extract {


  trait FieldMapper[A, B] {
    def apply(a: A): B
  }
  object FieldMapper {
    def apply[A, B](f: A => B): FieldMapper[A, B] = new FieldMapper[A, B] {
      def apply(a: A): B = f(a)
    }
    implicit def optionFieldMapper[A, B](implicit mapper: FieldMapper[A, B]): FieldMapper[Option[A], Option[B]] =
      FieldMapper { a: Option[A] =>
        a map (mapper(_))
      }
  }


  trait ExtractTo[A] {
    type Out
    def apply(a: A): Out
  }
  trait LowPriorityExtractTo {
    type Aux[A, B] = ExtractTo[A] { type Out = B; def apply(a: A): B }
  }
  object ExtractTo extends LowPriorityExtractTo {
    implicit def materialize[A, B]: Aux[A, B] = macro ExtractMacros.extractedToMaterialize[A, B]
  }



  trait ExtractedInto[A] {
    type Out
    def apply(a: A, b: Out): Out
  }
  object ExtractedInto {
    type Aux[A, B] = ExtractedInto[A] { type Out = B; def apply(a: A, b: B): B }
    implicit def materialize[A, B]: Aux[A, B] = macro ExtractMacros.extractedIntoMaterialize[A, B]
  }

  @macrocompat.bundle
  class ExtractMacros(val c: blackbox.Context) {

    import c.universe._

    private def fieldsMap(typ: Type): Map[TermName, Type] =
      typ.decls
        .collect { case m: MethodSymbol if m.isCaseAccessor => m.name -> m.info }
        .toMap

    // thanks to shapeless
    // Cut-n-pasted (with most original comments) and slightly adapted from
    // https://github.com/scalamacros/paradise/blob/c14c634923313dd03f4f483be3d7782a9b56de0e/plugin/src/main/scala/org/scalamacros/paradise/typechecker/Namers.scala#L568-L613
    def patchedCompanionSymbolOf(original: Symbol): Symbol = {
      // see https://github.com/scalamacros/paradise/issues/7
      // also see https://github.com/scalamacros/paradise/issues/64

      val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
      val typer = c.asInstanceOf[scala.reflect.macros.runtime.Context].callsiteTyper.asInstanceOf[global.analyzer.Typer]
      val ctx = typer.context
      val owner = original.owner

      import global.analyzer.Context

      original.companion.orElse {
        import global.{abort => aabort, _}
        implicit class PatchedContext(ctx: Context) {
          trait PatchedLookupResult { def suchThat(criterion: Symbol => Boolean): Symbol }
          def patchedLookup(name: Name, expectedOwner: Symbol) = new PatchedLookupResult {
            override def suchThat(criterion: Symbol => Boolean): Symbol = {
              var res: Symbol = NoSymbol
              var ctx = PatchedContext.this.ctx
              while (res == NoSymbol && ctx.outer != ctx) {
                // NOTE: original implementation says `val s = ctx.scope lookup name`
                // but we can't use it, because Scope.lookup returns wrong results when the lookup is ambiguous
                // and that triggers https://github.com/scalamacros/paradise/issues/64
                val s = {
                  val lookupResult = ctx.scope.lookupAll(name).filter(criterion).toList
                  lookupResult match {
                    case Nil => NoSymbol
                    case List(unique) => unique
                    case _ => aabort(s"unexpected multiple results for a companion symbol lookup for $original#{$original.id}")
                  }
                }
                if (s != NoSymbol && s.owner == expectedOwner)
                  res = s
                else
                  ctx = ctx.outer
              }
              res
            }
          }
        }
        ctx.patchedLookup(original.asInstanceOf[global.Symbol].name.companionName, owner.asInstanceOf[global.Symbol]).suchThat(sym =>
          (original.isTerm || sym.hasModuleFlag) &&
            (sym isCoDefinedWith original.asInstanceOf[global.Symbol])
        ).asInstanceOf[c.universe.Symbol]
      }
    }

    private def companionRef(tpe: Type): Tree = {
      val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
      val gTpe = tpe.asInstanceOf[global.Type]
      val pre = gTpe.prefix
      val cSym = patchedCompanionSymbolOf(tpe.typeSymbol).asInstanceOf[global.Symbol]
      if(cSym != NoSymbol)
        global.gen.mkAttributedRef(pre, cSym).asInstanceOf[Tree]
      else
        Ident(tpe.typeSymbol.name.toTermName) // Attempt to refer to local companion
    }
    //      Option(tpe.companion)
    //        .filter(_ != NoType)
    //        .map(t => q"$t")
    //        .getOrElse(Ident(tpe.typeSymbol.name.toTermName)) // Attempt to refer to local companion

    private def implicitFM(fromType: c.Type, toType: c.Type): c.Tree =
      c.typecheck(q"implicitly[_root_.io.github.steefh.amorphous.extract.FieldMapper[$fromType, $toType]]", silent = true)

    private def argsList(fromFields: Map[TermName, Type], toFields: Map[TermName, Type]) = {
      val implicitConversions = for {
        (name, toType) <- toFields
        fromType = fromFields(name)
        if !(fromType <:< toType)
      } yield (name, (fromType, toType, implicitFM(fromType, toType)))

      Some(for {
        (name, (fromType, toType, tree)) <- implicitConversions
        if tree == EmptyTree
      } yield s"Cannot find an implicit conversion for field '$name'.\n" +
        s"Make sure you have an implicit value of " +
        s"io.github.steefh.amorphous.extract.FieldMapper[${fromType.resultType}, ${toType.resultType}] " +
        s"in scope."
      ).filterNot(_.isEmpty).map(_.mkString("\n")).foreach(c.error(c.enclosingPosition, _))
      toFields.keys map { name =>
        implicitConversions.get(name).map {
          case (_, _, tree) => q"$name=$tree(a.$name)"
        } getOrElse {
          q"$name=a.$name"
        }
      }
    }


    def extractedToMaterialize[A: c.WeakTypeTag, B: c.WeakTypeTag]: c.Tree  = {

      val aType = weakTypeOf[A]
      val bType = weakTypeOf[B]
      val bCompanion = companionRef(bType)
      val fromFields = fieldsMap(aType)
      val toFields = fieldsMap(bType)


      val leftOverFields = fromFields.foldLeft(toFields){
        case (acc, (name, fieldType)) => acc - name
      }
      Some(leftOverFields.map {
        case (name, fieldType) =>
          s"${bType} contains a field '$name' of ${fieldType.resultType} that is not in ${aType}"
      }).filter(_.nonEmpty).map(_.mkString("\n")).foreach(c.error(c.enclosingPosition, _))

      val argList = argsList(fromFields, toFields)

      val result = q"""
        new _root_.io.github.steefh.amorphous.extract.ExtractTo[${aType}] {
          type Out = $bType
          def apply(a: $aType): $bType = ${bCompanion}(..${argList.toList})
        }
    """
      //    c.error(c.enclosingPosition, weakTypeOf[B].companion.toString)
      //          c.error(c.enclosingPosition, result.toString)
      //    q""
      result
    }

    def extractedIntoMaterialize[A: c.WeakTypeTag, B: c.WeakTypeTag]: c.Tree  = {

      val aType = weakTypeOf[A]
      val bType = weakTypeOf[B]
      val fromFields = fieldsMap(aType)
      val toFields = fieldsMap(bType)

      def intersect(a: Map[TermName, Type], b: Map[TermName, Type]): Map[TermName, Type] =
        b.keys.foldLeft(Map.empty[TermName, Type]) {
          case (acc, name) => a.get(name).map(t => acc + (name -> t)).getOrElse(acc)
        }

      val sharedFromFields = intersect(fromFields, toFields)
      val sharedToFields = intersect(toFields, fromFields)

      val argList = argsList(sharedFromFields, sharedToFields)

      val result = q"""
        new _root_.io.github.steefh.amorphous.extract.ExtractedInto[${aType}] {
          type Out = $bType
          def apply(a: $aType, b: $bType): $bType = b.copy(..${argList.toList})
        }
    """
      //    c.error(c.enclosingPosition, weakTypeOf[B].companion.toString)
      //                c.error(c.enclosingPosition, result.toString)
      //    q""
      result
    }
  }

  implicit class ExtractToSyntax[A](value: A) {
    def extractedTo[C](implicit op: ExtractTo.Aux[A, C]): C = op(value)
  }

  implicit class ExtractedIntoSyntax[A](value: A) {
    def extractedInto[B](b: B)(implicit op: ExtractedInto.Aux[A, B]): B = op(value, b)
  }
}
