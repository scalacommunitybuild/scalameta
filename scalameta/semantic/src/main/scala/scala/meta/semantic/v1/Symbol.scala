package scala.meta
package semantic
package v1

import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.compat.Platform.EOL
import org.scalameta.adt._
import org.scalameta.invariants._
import scala.meta.common._
import scala.meta.internal.semantic.v1._

// NOTE: This is an initial take on the semantic API.
// Instead of immediately implementing the full vision described in my dissertation,
// we will first deliver the low-hanging fruit (https://github.com/scalameta/scalameta/issues/604),
// and only then will approach really tricky tasks (https://github.com/scalameta/scalameta/issues/623).

@root trait Symbol extends Optional {
  def syntax: String
  def structure: String
}

object Symbol {
  @none object None extends Symbol {
    override def syntax = s""
    override def structure = s"""Symbol.None"""
    override def toString = syntax
  }

  @leaf class Local(addr: Address, start: Int, end: Int) extends Symbol {
    override def syntax = s"${addr.syntax}@$start..$end"
    override def structure = s"""Symbol.Local(${addr.structure}, $start, $end)"""
    override def toString = syntax
  }

  @leaf class Global(owner: Symbol, signature: Signature) extends Symbol {
    override def syntax = s"${owner.syntax}${signature.syntax}"
    override def structure = s"""Symbol.Global(${owner.structure}, ${signature.structure})"""
    override def toString = syntax
  }

  @leaf class Multi(symbols: Seq[Symbol] @nonEmpty) extends Symbol {
    override def syntax = symbols.map(_.syntax).mkString(";")
    override def structure = s"""Symbol.Multi(${symbols.map(_.structure).mkString(", ")})"""
    override def toString = syntax
  }

  // TODO: This is obviously a very naive implementation.
  // It'll do for prototyping, but in the future we'll have to replace it.
  // upd. Ugh, I should've started with fastparse in the first place!!
  def apply(s: String): Symbol = {
    object naiveParser {
      var i = 0
      def fail() = {
        val message = "invalid symbol format"
        val caret = " " * (i - 1) + "^"
        sys.error(s"$message$EOL$s$EOL$caret")
      }

      val BOF = '\u0000'
      val EOF = '\u001A'
      var currChar = BOF
      def readChar(): Char = {
        if (i >= s.length) {
          if (i == s.length) {
            currChar = EOF
            i += 1
            currChar
          } else {
            fail()
          }
        } else {
          currChar = s(i)
          i += 1
          currChar
        }
      }

      def parseName(): String = {
        val buf = new StringBuilder()
        if (currChar == '`') {
          while (readChar() != '`') buf += currChar
          readChar()
        } else {
          if (!Character.isJavaIdentifierStart(currChar)) fail()
          buf += currChar
          while (Character.isJavaIdentifierPart(readChar())) buf += currChar
        }
        buf.toString
      }

      def parseGlobal(curr: Symbol): Symbol = {
        if (currChar == EOF) {
          curr
        } else if (currChar == ';') {
          curr
        } else if (currChar == '[') {
          readChar()
          val name = parseName()
          if (currChar != ']') fail()
          else readChar()
          parseGlobal(Symbol.Global(curr, Signature.TypeParameter(name)))
        } else if (currChar == '(') {
          readChar()
          val name = parseName()
          if (currChar != ')') fail()
          else readChar()
          parseGlobal(Symbol.Global(curr, Signature.TermParameter(name)))
        } else {
          val name = parseName()
          if (currChar == '#') {
            readChar()
            parseGlobal(Symbol.Global(curr, Signature.Type(name)))
          } else if (currChar == '.') {
            readChar()
            parseGlobal(Symbol.Global(curr, Signature.Term(name)))
          } else if (currChar == '(') {
            val buf = new StringBuilder()
            buf += currChar
            while (readChar() != '.') buf += currChar
            readChar()
            parseGlobal(Symbol.Global(curr, Signature.Method(name, buf.toString)))
          } else if (currChar == '=') {
            readChar()
            if (currChar != '>') fail()
            else readChar()
            parseGlobal(Symbol.Global(curr, Signature.Self(name)))
          } else {
            fail()
          }
        }
      }
      def parseLocal(): Symbol = {
        val addrBuf = new StringBuilder
        addrBuf += currChar
        while (readChar() != '@') addrBuf += currChar

        val startBuf = new StringBuilder
        while (Character.isDigit(readChar())) startBuf += currChar

        if (currChar != '.') fail()
        readChar()
        if (currChar != '.') fail()

        val endBuf = new StringBuilder
        while (Character.isDigit(readChar())) endBuf += currChar

        Symbol.Local(Address(addrBuf.toString), startBuf.toString.toInt, endBuf.toString.toInt)
      }

      def parseMulti(symbols: List[Symbol]): Symbol = {
        if (currChar == EOF) {
          symbols match {
            case Nil => Symbol.None
            case List(symbol) => symbol
            case symbols => Symbol.Multi(symbols)
          }
        } else {
          val symbol = {
            if (currChar == '_') parseGlobal(Symbol.None)
            else parseLocal()
          }
          if (currChar == ';') {
            readChar()
            if (currChar == EOF) fail()
          }
          parseMulti(symbols :+ symbol)
        }
      }

      def entryPoint(): Symbol = {
        readChar()
        parseMulti(Nil)
      }
    }
    naiveParser.entryPoint()
  }
}

@root trait Signature {
  def name: String
  def syntax: String
  def structure: String
}

object Signature {
  @leaf class Type(name: String) extends Signature {
    override def syntax = s"${encodeName(name)}#"
    override def structure = s"""Signature.Type("$name")"""
    override def toString = syntax
  }

  @leaf class Term(name: String) extends Signature {
    override def syntax = s"${encodeName(name)}."
    override def structure = s"""Signature.Term("$name")"""
    override def toString = syntax
  }

  @leaf class Method(name: String, jvmSignature: String) extends Signature {
    override def syntax = s"${encodeName(name)}$jvmSignature."
    override def structure = s"""Signature.Method("$name", "$jvmSignature")"""
    override def toString = syntax
  }

  @leaf class TypeParameter(name: String) extends Signature {
    override def syntax = s"[${encodeName(name)}]"
    override def structure = s"""Signature.TypeParameter("$name")"""
    override def toString = syntax
  }

  @leaf class TermParameter(name: String) extends Signature {
    override def syntax = s"(${encodeName(name)})"
    override def structure = s"""Signature.TermParameter("$name")"""
    override def toString = syntax
  }

  @leaf class Self(name: String) extends Signature {
    override def syntax = s"${encodeName(name)}=>"
    override def structure = s"""Signature.Self("$name")"""
    override def toString = syntax
  }

  private def encodeName(name: String): String = {
    if (name.forall(Character.isJavaIdentifierStart)) name else "`" + name + "`"
  }
}