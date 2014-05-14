package org.nkvoll.javabin.models.parsing

import org.parboiled.scala._
import org.parboiled.scala.parserunners.BasicParseRunner

sealed trait AstNode {
  def asString: String
}

case class ContentsNode(members: List[AstNode]) {
  def asString = members.map(_.asString).mkString
}

case class DefineNode(query: String) extends AstNode {
  def asString = s"""!define:\"$query\""""
}

case class WikiNode(page: String) extends AstNode {
  def asString = s"""!wiki:\"$page\""""
}

case class TextNode(data: String) extends AstNode {
  def asString = data
}

sealed trait CalcNode extends AstNode {
  def value: Int

  def asString = value.toString + " "
}

case class ValueNode(value: Int) extends CalcNode

case class OperatorNode(operator: Char, left: CalcNode, right: CalcNode) extends CalcNode {
  def value: Int = {
    operator match {
      case '+' => left.value + right.value
      case '-' => left.value - right.value
      case '*' => left.value * right.value
      case '/' => left.value / right.value
    }
  }
}

trait MessageContentsParser extends Parser with DefineParser with CalculatorParser with WikiParser {
  def Contents: Rule1[ContentsNode] = rule { oneOrMore(Define | Calc | Wiki | Text) ~~> ContentsNode }
  def Text: Rule1[TextNode] = rule { (Word | WhiteSpaceString | QuotedString | QuoteString) ~~> TextNode }
}

trait CommonParser extends Parser {
  def Word: Rule1[String] = rule { oneOrMore(Character) ~> identity }

  def QuotedString = rule { Quoted ~> identity }
  def Quoted = rule { Quote ~ zeroOrMore(Character | WhiteSpace) ~ Quote }
  def QuotedStringContents: Rule1[String] = rule { Quote ~ zeroOrMore(Character | WhiteSpace) ~> identity ~ Quote }

  def QuoteString: Rule1[String] = rule { Quote ~> identity }
  def Quote = rule { str("\"") }

  def WhiteSpaceString: Rule1[String] = rule { WhiteSpace ~> identity }
  def WhiteSpace = rule { oneOrMore(anyOf(" \n\r\t\f")) }
  def MaybeWhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  def Character = rule { EscapedChar | NormalChar }
  def EscapedChar = rule { "\\" ~ (anyOf("\"\\/bfnrt") | Unicode) }
  def NormalChar = rule { !anyOf("\"\\ ") ~ ANY }
  def Unicode = rule { "u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit }
  def HexDigit = rule { "0" - "9" | "a" - "f" | "A" - "Z" }
}

trait DefineParser extends CommonParser {
  def Define: Rule1[DefineNode] = rule { str("!define:") ~ MaybeWhiteSpace ~ DefineToken ~~> DefineNode.apply }
  def DefineToken: Rule1[String] = rule { Word | QuotedStringContents }
}

trait WikiParser extends CommonParser {
  def Wiki: Rule1[WikiNode] = rule { str("!wiki:") ~ MaybeWhiteSpace ~ WikiToken ~~> WikiNode.apply }
  def WikiToken: Rule1[String] = rule { Word | QuotedStringContents }
}

trait CalculatorParser extends CommonParser {
  def Calc: Rule1[CalcNode] = rule { str("!calc:") ~ MaybeWhiteSpace ~ CalculatorExpression }
  def CalculatorExpression: Rule1[CalcNode] = rule { Term ~ zeroOrMore(anyOf("+-") ~:> identity ~ Term) ~~> collectOperations }

  def Term: Rule1[CalcNode] = rule { Atom ~ zeroOrMore(anyOf("*/") ~:> identity ~ Atom) ~~> collectOperations }

  def Atom: Rule1[CalcNode] = rule { Digits | Parens }
  def Parens: Rule1[CalcNode] = rule { "(" ~ CalculatorExpression ~ ")" }
  def Digits: Rule1[CalcNode] = rule { MaybeWhiteSpace ~ SignedDigits ~> (num => ValueNode(Integer.parseInt(num))) ~ MaybeWhiteSpace }
  def SignedDigits: Rule0 = rule { zeroOrMore(ch('-')) ~ oneOrMore(Digit) }
  def Digit = rule { "0" - "9" }

  def collectOperations(first: CalcNode, rest: List[(Char, CalcNode)]): CalcNode = {
    rest.foldLeft(first) {
      case (a, (op, b)) => OperatorNode(op, a, b)
    }
  }
}

object MessageContentsParser extends MessageContentsParser {
  def parseBasic(input: String): Option[ContentsNode] =
    BasicParseRunner(MessageContentsParser.Contents).run(input).result
}