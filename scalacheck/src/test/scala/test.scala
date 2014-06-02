import org.specs2.specification.BeforeExample
import org.specs2.mutable.{Specification}
import org.specs2.ScalaCheck
import org.scalacheck._

class Testing extends Specification with ScalaCheck {
  "true" should {
    "always be true" in check {
      true mustEqual true
    }
  }

}
