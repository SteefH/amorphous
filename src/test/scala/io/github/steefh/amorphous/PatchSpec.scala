package io.github.steefh.amorphous

import org.scalatest._

class PatchSpec extends WordSpec with Matchers {
  import patch._

  case class Foo(intValue: Int, stringValue: String, optionalBoolean: Option[Boolean])

  case class FooUpdate(intValue: Int)

  case class FooUpdateWithOptions(intValue: Option[Int], stringValue: Option[String])

  case class CannotUpdateFoo(r: Boolean) // field r is not in Foo

  "patching an object of type A" when {
    "the original has a field of a non-Option type T" which {
      case class Obj(intValue: Int, stringValue: String)
      val original = Obj(1, "a")
      "is patched with an object of type A" should {
        val patch = Obj(2, "b")
        "result in the patching object" in {

          original patchedWith patch shouldBe patch
        }
      }
      "is patched with an object of type B unrelated to A" which {
        "has a field of type T with the same name as in A" should {
          case class Patch(intValue: Int)
          "update the field with the patching object's field value" in {
            val patch = Patch(2)
            original patchedWith patch shouldBe original.copy(intValue = patch.intValue)
          }
        }
        "has a field of type Option[T] with the same name as in A" which {
          case class Patch(intValue: Option[Int])
          "is set to None" should {
            val patch = Patch(None)
            "not update the field in the original" in {
              original patchedWith patch shouldBe original
            }
          }
          "is set to Some(x)" should {
            val patch = Patch(Some(2))
            "update the field to x in the original" in {
              original patchedWith patch shouldBe original.copy(intValue = patch.intValue.get)
            }
          }
        }
      }
    }
    "the original has a field of type Option[T]" which {
      case class Obj(intValue: Option[Int], stringValue: String)
      "is set to None" which {
        val original = Obj(None, "a")
        "is patched with an object of the type A" should {
          val patch = Obj(Some(1), "b")
          "result in the patching object" in {
            original patchedWith patch shouldBe patch
          }
        }
        "is patched with an object of type B unrelated to A" which {
          "has a field of type Option[T] with the same name as in A" which {
            case class Patch(intValue: Option[Int])
            "is set to None" should {
              val patch = Patch(None)
              "not update the field in the original" in {
                original patchedWith patch shouldBe original
              }
            }
            "is set to Some(x)" should {
              val patch = Patch(Some(2))
              "update the field in the original to Some(x)" in {
                original patchedWith patch shouldBe original.copy(intValue = patch.intValue)
              }
            }
          }
        }
      }
      "is set to Some(x)" which {
        val original = Obj(Some(1), "a")
        "is patched with an object of the type A" should {
          val patch = Obj(Some(2), "b")
          "result in the patching object" in {
            original patchedWith patch shouldBe patch
          }
        }
        "is patched with an object of type B unrelated to A" which {
          "has a field of type Option[T] with the same name as in A" which {
            case class Patch(intValue: Option[Int])
            "is set to None" should {
              val patch = Patch(None)
              "not update the field in the original" in {
                original patchedWith patch shouldBe original
              }
            }
            "is set to Some(x)" should {
              val patch = Patch(Some(2))
              "update the field in the original to Some(x)" in {
                original patchedWith patch shouldBe original.copy(intValue = patch.intValue)
              }
            }
          }
        }
      }
    }
  }
  "patching an object of type A" when {
    case class NestedObject(intValue: Int, stringValue: String)
    "the original has a field of the nested type N" which {
      case class WithNestedObject(nested: NestedObject)

      val original = WithNestedObject(NestedObject(1, "a"))

      "is patched with an object of type B unrelated to A" should {
        case class PatchWithNestedObject(nested: NestedObject)
        "replace the nested object with the patching object" in {
          original patchedWith PatchWithNestedObject(NestedObject(2, "q")) shouldBe WithNestedObject(NestedObject(2, "q"))
        }
      }
      "is patched with a patching type" should {
        case class NestedPatch(intValue: Int)
        case class PatchWithNestedPatch(nested: NestedPatch)
        "patch the nested object with the fields from the patching object" in {
          original patchedWith PatchWithNestedPatch(NestedPatch(2)) shouldBe WithNestedObject(NestedObject(2, "a"))
        }
      }
      "is patched with an Option field" which {
        "is of the same type" which {
          case class PatchWithNestedObjectOption(nested: Option[NestedObject])
          "is set to None" should {
            "not patch the nested object" in {
              original patchedWith PatchWithNestedObjectOption(None) shouldBe WithNestedObject(NestedObject(1, "a"))
            }
          }
          "is set to Some(x)" should {
            "replace the nested object with x" in {
              original patchedWith PatchWithNestedObjectOption(Some(NestedObject(2, "b"))) shouldBe WithNestedObject(NestedObject(2, "b"))
            }
          }
        }
        "is of a patching type" which {
          case class NestedPatch(intValue: Int)
          case class PatchWithNestedPatchOption(nested: Option[NestedPatch])
          "is set to None" should {
            "not patch the nested object" in {
              original patchedWith PatchWithNestedPatchOption(None) shouldBe WithNestedObject(NestedObject(1, "a"))
            }
          }
          "is set to Some(x)" should {
            "patch the nested object with x" in {
              original patchedWith PatchWithNestedPatchOption(Some(NestedPatch(2))) shouldBe WithNestedObject(NestedObject(2, "a"))
            }
          }
        }
      }
    }
    "the original has a nested Option field" which {
      case class WithNestedObjectOption(nested: Option[NestedObject])
      "is set to Some(x)" which {
        val original = WithNestedObjectOption(Some(NestedObject(1, "a")))

        "is patched with an option field" which {
          "is of the same type" which {
            case class PatchWithNestedObjectOption(nested: Option[NestedObject])
            "is set to None" should {
              "not patch the nested Option object" in {
                original patchedWith PatchWithNestedObjectOption(None) shouldBe original
              }
            }
            "is set to Some(y)" should {
              "set the field to Some(y)" in {
                (original patchedWith PatchWithNestedObjectOption(Some(NestedObject(2, "b")))) shouldBe WithNestedObjectOption(Some(NestedObject(2, "b")))
              }
            }
          }
          "is of a patching type" which {
            case class NestedPatch(intValue: Int)
            case class PatchWithNestedObjectOption(nested: Option[NestedPatch])
            "is set to None" should {
              "not patch the nested Option object" in {
                (original patchedWith PatchWithNestedObjectOption(None)) shouldBe WithNestedObjectOption(Some(NestedObject(1, "a")))
              }
            }
            "is set to Some(y)" should {
              "set the field to Some(x patchedWith y)" in {
                val patch = PatchWithNestedObjectOption(Some(NestedPatch(2)))
                val newNested = Some(original.nested.get patchedWith patch.nested.get)
                (original patchedWith patch) shouldBe WithNestedObjectOption(newNested)
              }
            }
          }
        }
      }
      "is set to None" which {
        val original = WithNestedObjectOption(None)

        "is patched with an Option field" which {
          "is of the same type" which {
            case class PatchWithNestedObjectOption(nested: Option[NestedObject])
            "is set to None" should {
              "set the field to None" in {
                original patchedWith PatchWithNestedObjectOption(None) shouldBe WithNestedObjectOption(None)

              }
            }
            "is set to Some(x)" should {
              "set the field to Some(x)" in {
                (original patchedWith PatchWithNestedObjectOption(Some(NestedObject(2, "b")))) shouldBe WithNestedObjectOption(Some(NestedObject(2, "b")))
              }
            }
          }
          "is of a patching type" which {
            case class NestedPatch(intValue: Int)
            case class PatchWithNestedObjectOption(nested: Option[NestedPatch])
            "is set to None" should {
              "set the field to None" in {
                (original patchedWith PatchWithNestedObjectOption(None)) shouldBe WithNestedObjectOption(None)
              }
            }
            "is set to Some(x)" should {
              "set the field to None" in {
                (original patchedWith PatchWithNestedObjectOption(Some(NestedPatch(2)))) shouldBe WithNestedObjectOption(None)
              }
            }
          }
        }
      }
    }
  }


  "using patchWith with incompatible case classes" should {
    "fail compilation" in {
      """
         Foo(1, "a", None) patchedWith FooUpdate(2)
      """ should compile
      """
         Foo(1, "a", None) patchedWith CannotUpdateFoo(false)
      """ shouldNot typeCheck
    }
  }
}
