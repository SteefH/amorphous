package io.github.steefh.amorphous

import org.scalatest._

class ExtractSpec extends WordSpec with Matchers {

  import extract._

  "extractedTo" should {
    "extract fields from the source instance and put them in the target type's instance" in {
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

}
