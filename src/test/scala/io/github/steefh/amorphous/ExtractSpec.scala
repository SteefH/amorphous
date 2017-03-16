package io.github.steefh.amorphous

import org.scalatest._

class ExtractSpec extends WordSpec with Matchers {

  import extract._

  "extractedTo" should {
    "extract fields from the source instance and put them in a target type's instance" in {
      case class Foo(i: Int, s: String, b: Boolean)
      case class Bar(b: Boolean, i: Int)
      Foo(1, "s", false).extractedTo[Bar] shouldBe Bar(false, 1)
    }
    "not compile when the extract target contains fields not in the source" in {
      """
        | case class Source(i: Int)
        | case class Target(s: String)
        |
        | Source(1).extractedTo[Target]
      """.stripMargin shouldNot typeCheck
    }
    "not compile when the extract target fields are not of the same type as the fields in the source" in {
      """
        | case class Source(i: Long)
        | case class Target(i: Int)
        |
        | Source(1L).extractedTo[Target]
      """.stripMargin
    }
  }
  "extractedInto" should {
    "extract fields from the source case class instance and put them in a copy of the target instance" in {
      case class Foo(i: Int, b: Boolean)
      case class Bar(si: String, n: Boolean)
      case class Baz(b: Boolean, n: Int)
      case class FooPlus(i: Int, b: Boolean, extra: String)
      Baz(b = false, n = 2) extractedInto Foo(i = 0, b = true) shouldBe Foo(i = 0, b = false)
      Bar(si = "alse", n = false) extractedInto Foo(i = 0, b = true) shouldBe Foo(i = 0, b = true)
      Foo(i = 0, b = true) extractedInto Bar(si = "fafas", n = true) shouldBe Bar(si = "fafas", n = true)
      Foo(i = 0, b = true) extractedInto FooPlus(i = 1, b = false, extra = "extra") shouldBe FooPlus(i = 0, b = true, extra = "extra")
      FooPlus(i = 0, b = true, extra = "extra") extractedInto Foo(i = 1, b = false) extractedInto Foo(i = 0, b = true)
    }
    "stuff" in {
      case class Foo(i: Int, n: Option[Long], b: Boolean)
      case class Bar(i: String, n: Option[Long])
//      implicit val intToString: FieldMapper[Int, String] = ExtractFieldMapper {n: Int => n.toString }
      implicit val intToString: FieldMapper[Int, String] = FieldMapper {n: Int => n.toString }
      Foo(1, n = Some(3L), b = false) extractedInto Bar("", None) shouldBe Bar("1", Some(3L))
    }

    "options" in {
      case class Foo(i: Option[Int])
      case class Bar(i: Option[Long])
      implicit val ll: FieldMapper[Int, Long] = FieldMapper {n: Int => n.toLong}
      Foo(None).extractedTo[Bar]
    }
  }

}
