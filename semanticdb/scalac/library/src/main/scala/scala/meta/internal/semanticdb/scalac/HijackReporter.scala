package scala.meta.internal.semanticdb.scalac

import scala.tools.nsc.reporters.FilteringReporter

trait HijackReporter { self: SemanticdbPlugin =>

  def hijackReporter(): Unit = {
    if (!isSupportedCompiler) return

    g.reporter match {
      case _: SemanticdbReporter => // do nothing, already hijacked
      case underlying: FilteringReporter =>
        val semanticdbReporter = new SemanticdbReporter(underlying)
        g.reporter = semanticdbReporter
    }
  }
}
