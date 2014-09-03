import org.scalatest._

class ScalaToMeta extends ToolboxHarness {
  test("definition of old Join") {
    checkScalaToMeta("""
      |import scala.reflect.macros.whitebox._
      |import scala.language.experimental.macros
      |
      |object Join {
      |  def impl(c: Context)(x: c.Tree, y: c.Tree) = {
      |    import c.universe._
      |    def fields(tree: Tree) = tree.tpe.members.collect{ case m: TermSymbol if m.isGetter => m }
      |    val xfields = fields(x).map(f => f -> q"xtemp")
      |    val yfields = fields(y).map(f => f -> q"ytemp")
      |    val getters = (xfields ++ yfields).map{ case (f, ref) => q"val ${f.name} = $ref.${f.name}" }
      |    qQQQ
      |      val xtemp = $x
      |      val ytemp = $y
      |      new { ..$getters }
      |    QQQ
      |  }
      |
      |  def apply[T, U](x: T, y: U): Any = macro impl
      |}
    """,
    """
      |import scala.reflect.macros.whitebox._
      |import scala.language.experimental.macros
      |object Join {
      |  def impl(c: Context)(x: c.Tree, y: c.Tree) = {
      |    import c.universe._
      |    def fields(tree: Tree) = tree.tpe.members.collect({
      |      case m: TermSymbol if m.isGetter =>
      |        m
      |    })
      |    val xfields = fields(x).map(f => f -> q"xtemp")
      |    val yfields = fields(y).map(f => f -> q"ytemp")
      |    val getters = (xfields ++ yfields).map({
      |      case (f, ref) =>
      |        q"val ${f.name} = $ref.${f.name}"
      |    })
      |    qQQQ
      |      val xtemp = $x
      |      val ytemp = $y
      |      new { ..$getters }
      |    QQQ
      |  }
      |  def apply[T, U](x: T, y: U): Any = macro Join.impl
      |}
    """)
  }

  test("attachment-checking plugin") {
    checkScalaToMeta("""
      |import scala.tools.nsc.{Global, Phase, SubComponent}
      |import scala.tools.nsc.plugins.{Plugin => NscPlugin, PluginComponent => NscPluginComponent}
      |import org.scalameta.reflection.Metadata
      |
      |class Plugin(val global: Global) extends NscPlugin with Metadata { self =>
      |  val name = "attatest"
      |  val description = "Tests attachments produced by scalahost"
      |  val components = List[NscPluginComponent](PluginComponent)
      |  object PluginComponent extends NscPluginComponent {
      |    val global: self.global.type = self.global
      |    import global._
      |
      |    override val runsAfter = List("typer")
      |    override val runsRightAfter = None
      |    val phaseName = "attatest"
      |    override def description = "tests attachments produced by scalahost"
      |
      |    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      |      import analyzer._
      |      override def apply(unit: CompilationUnit): Unit = {
      |        unit.body.foreach(tree => {
      |          if (hasMacroExpansionAttachment(tree)) {
      |            tree.metadata.toOption match {
      |              case None =>
      |                reporter.error(tree.pos, "macro expansion without a property bag")
      |              case Some(bag) =>
      |                if (!bag.contains("expandeeTree")) reporter.error(tree.pos, "macro expansion without expandeeTree in the property bag")
      |                if (!bag.contains("expandedTree")) reporter.error(tree.pos, "macro expansion without expandedTree in the property bag")
      |                if (!bag.contains("expansionString")) reporter.error(tree.pos, "macro expansion without expansionString in the property bag")
      |            }
      |          }
      |        })
      |      }
      |    }
      |  }
      |}
    """,
    """
      |import scala.tools.nsc.{ Global, Phase, SubComponent }
      |import scala.tools.nsc.plugins.{ Plugin => NscPlugin, PluginComponent => NscPluginComponent }
      |import org.scalameta.reflection.Metadata
      |class Plugin(val global: Global) extends NscPlugin with Metadata { self =>
      |  val name = "attatest"
      |  val description = "Tests attachments produced by scalahost"
      |  val components = List[NscPluginComponent](PluginComponent)
      |  object PluginComponent extends NscPluginComponent {
      |    val global: self.global.type = self.global
      |    import global._
      |    override val runsAfter = List("typer")
      |    override val runsRightAfter = None
      |    val phaseName = "attatest"
      |    override def description = "tests attachments produced by scalahost"
      |    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      |      import analyzer._
      |      override def apply(unit: CompilationUnit): Unit = unit.body.foreach(tree => if (hasMacroExpansionAttachment(tree)) tree.metadata.toOption match {
      |        case None =>
      |          reporter.error(tree.pos, "macro expansion without a property bag")
      |        case Some(bag) =>
      |          if (!bag.contains("expandeeTree")) reporter.error(tree.pos, "macro expansion without expandeeTree in the property bag");
      |          if (!bag.contains("expandedTree")) reporter.error(tree.pos, "macro expansion without expandedTree in the property bag");
      |          if (!bag.contains("expansionString")) reporter.error(tree.pos, "macro expansion without expansionString in the property bag")
      |      })
      |    }
      |  }
      |}
    """)
  }

  test("old Join test") {
    checkScalaToMeta("""
      |import org.scalatest._
      |
      |class OldMacros extends FunSuite {
      |  test("old join") {
      |    import scala.language.reflectiveCalls
      |    val x = new { val x = 2 }
      |    val y = new { val y = 3 }
      |    val result = Join(x, y)
      |    assert(result.x === 2)
      |    assert(result.y === 3)
      |  }
      |}
    """,
    """
      |import org.scalatest._
      |class OldMacros extends FunSuite {
      |  test("old join") {
      |    import scala.language.reflectiveCalls
      |    val x = new { val x = 2 }
      |    val y = new { val y = 3 }
      |    val result = Join(x, y)
      |    assert(result.x === 2)
      |    assert(result.y === 3)
      |  }
      |}
    """)
  }

  test("new Join test") {
    checkScalaToMeta("""
      |import org.scalatest._
      |
      |import scala.language.reflectiveCalls
      |import scala.language.experimental.macros
      |import scala.meta._
      |import scala.meta.semantic._
      |import scala.meta.semantic.errors.throwExceptions
      |
      |class NewMacros extends FunSuite {
      |  test("new join") {
      |    def join[T, U](x: T, y: U): Any = macro {
      |      val xfields = x.tpe.vals.map(f => f -> q"xtemp")
      |      val yfields = y.tpe.vals.map(f => f -> q"ytemp")
      |      val getters = (xfields ++ yfields).map{ case (f, ref) => q"val ${f.name} = $ref.${f.name}" }
      |      c.whitebox(qQQQ
      |        val xtemp = $x
      |        val ytemp = $y
      |        new { ..$getters }
      |      QQQ)
      |    }
      |    val result = join(new { val x = 2 }, new { val y = 3 })
      |    assert(result.x === 2)
      |    assert(result.y === 3)
      |  }
      |}
    """,
    """
      |import org.scalatest._
      |import scala.language.reflectiveCalls
      |import scala.language.experimental.macros
      |import scala.meta._
      |import scala.meta.semantic._
      |import scala.meta.semantic.errors.throwExceptions
      |class NewMacros extends FunSuite {
      |  test("new join") {
      |    def join[T, U](x: T, y: U): Any = macro {
      |      val xfields = x.tpe.vals.map(f => f -> q"xtemp")
      |      val yfields = y.tpe.vals.map(f => f -> q"ytemp")
      |      val getters = (xfields ++ yfields).map({
      |        case (f, ref) =>
      |          q"val ${f.name} = $ref.${f.name}"
      |      })
      |      c.whitebox(qQQQ
      |        val xtemp = $x
      |        val ytemp = $y
      |        new { ..$getters }
      |      QQQ)
      |    }
      |    val result = join(new { val x = 2 }, new { val y = 3 })
      |    assert(result.x === 2)
      |    assert(result.y === 3)
      |  }
      |}
    """)
  }
}
