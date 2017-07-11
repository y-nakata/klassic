package com.github.klassic

import com.github.klassic.AST.{FunctionCall, ListLiteral, MapLiteral, ObjectNew, _}
import com.github.klassic.Type.{BooleanType, DynamicType}

/**
  * @author Kota Mizushima
  */
class SyntaxRewriter extends Processor[AST.Program, AST.Program] {
  object SymbolGenerator {
    private[this] var counter: Int = 0
    def symbol(): String = {
      val name = "var" + counter
      counter += 1
      name
    }
  }

  import SymbolGenerator.symbol


  def doRewrite(node: AST): AST = node match {
    case Block(location, expressions) =>
      def rewriteBlock(es: List[AST]): List[AST] = es match {
        case ValDeclaration(location, variable, description, value, immutable) :: xs =>
          List(Let(location, variable, description, doRewrite(value), Block(location, rewriteBlock(xs)), immutable))
        case FunctionDefinition(loation, name, expression, cleanup) :: xs =>
          List(LetRec(location, name, doRewrite(expression).asInstanceOf[AST.Lambda], cleanup.map(doRewrite), Block(location, rewriteBlock(xs))))
        case (x@VariantDeclaration(_, _, _, _)) :: xs =>
          List(VariantIn(x.location, x, Block(location, rewriteBlock(xs))))
        case x :: xs =>
          doRewrite(x) :: rewriteBlock(xs)
        case Nil =>
          Nil
      }
      Block(location, rewriteBlock(expressions))
    case IfExpression(location, cond, pos, neg) =>
      IfExpression(location, doRewrite(cond), doRewrite(pos), doRewrite(neg))
    case WhileExpression(location, condition, body: AST) =>
      WhileExpression(location, doRewrite(condition), doRewrite(body))
    case RecordSelect(location, expression, member) =>
      RecordSelect(location, doRewrite(expression), member)
    case RecordNew(location, name, members) =>
      RecordNew(location, name, members.map({ case e => doRewrite(e) }))
    case e@ForeachExpression(location, name, collection, body) =>
      val itVariable = symbol()
      val location = e.location
      Block(location, List(
        Let(
          location, itVariable, None, MethodCall(location, Casting(location, doRewrite(collection), DynamicType), "iterator", List()),
          WhileExpression(
            location,
            BinaryExpression(
              location,
              Operator.EQUAL,
              Casting(location, MethodCall(location, Id(location, itVariable), "hasNext", List()), BooleanType),
              BooleanNode(location, true)
            ),
            Block(location, List(
              Let(location, name, None, MethodCall(location, Id(location, itVariable), "next", List()), doRewrite(body), false)
            ))
          ),
          false)
      ))
    case BinaryExpression(location, operator, lhs, rhs) =>
      BinaryExpression(location, operator, doRewrite(lhs), doRewrite(rhs))
    case MinusOp(location, operand) => MinusOp(location, doRewrite(operand))
    case PlusOp(location, operand) => PlusOp(location, doRewrite(operand))
    case literal@StringNode(location, value) => literal
    case literal@IntNode(location, value) => literal
    case literal@LongNode(location, value)  => literal
    case literal@ShortNode(location, value) => literal
    case literal@ByteNode(location, value) => literal
    case literal@BooleanNode(location, value) => literal
    case literal@DoubleNode(location, value) => literal
    case literal@FloatNode(lcation, value) => literal
    case node@Id(_, _) => node
    case node@Selector(_, _, _) => node
    case SimpleAssignment(location, variable, value) => SimpleAssignment(location, variable, doRewrite(value))
    case PlusAssignment(location, variable, value) =>
      val generatedSymbol = symbol()
      val rewritedValue = doRewrite(value)
      Block(
        location,
        List(
          Let(location, generatedSymbol, None, rewritedValue,
            SimpleAssignment(location, variable,
              BinaryExpression(location, Operator.ADD, Id(location, variable), Id(location, generatedSymbol) )
            ),
            true
          )
        )
      )
    case MinusAssignment(location, variable, value) =>
      val generatedSymbol = symbol()
      val rewritedValue = doRewrite(value)
      Block(
        location,
        List(
          Let(location, generatedSymbol, None, rewritedValue,
            SimpleAssignment(location, variable,
              BinaryExpression(location, Operator.SUBTRACT, Id(location, variable), Id(location, generatedSymbol) )
            ),
            true
          )
        )
      )
    case MultiplicationAssignment(location, variable, value) =>
      val generatedSymbol = symbol()
      val rewritedValue = doRewrite(value)
      Block(
        location,
        List(
          Let(location, generatedSymbol, None, rewritedValue,
            SimpleAssignment(location, variable,
              BinaryExpression(location, Operator.MULTIPLY, Id(location, variable), Id(location, generatedSymbol) )
            ),
            true
          )
        )
      )
    case DivisionAssignment(location, variable, value) =>
      val generatedSymbol = symbol()
      val rewritedValue = doRewrite(value)
      Block(
        location,
        List(
          Let(location, generatedSymbol, None, rewritedValue,
            SimpleAssignment(location, variable,
              BinaryExpression(location, Operator.DIVIDE, Id(location, variable), Id(location, generatedSymbol) )
            ),
            true
          )
        )
      )
    case ValDeclaration(location, variable, optionalType, value, immutable) => ValDeclaration(location, variable, optionalType, doRewrite(value), immutable)
    case Lambda(location, params, optionalType, proc) => Lambda(location, params, optionalType, doRewrite(proc))
    case FunctionCall(location, func, params) => FunctionCall(location, doRewrite(func), params.map{doRewrite})
    case ListLiteral(location, elements) =>  ListLiteral(location, elements.map{doRewrite})
    case SetLiteral(location, elements) =>  SetLiteral(location, elements.map{doRewrite})
    case MapLiteral(location, elements) => MapLiteral(location, elements.map{ case (k, v) => (doRewrite(k), doRewrite(v))})
    case ObjectNew(location, className, params) => ObjectNew(location, className, params.map{doRewrite})
    case MethodCall(location ,self, name, params) => MethodCall(location, doRewrite(self), name, params.map{doRewrite})
    case Casting(location, target, to) => Casting(location, doRewrite(target), to)
    case otherwise => throw RewriterPanic(otherwise.toString)
  }

  def transform(program: AST.Program): AST.Program = {
    program.copy(block = doRewrite(program.block).asInstanceOf[AST.Block])
  }

  override final val name: String = "Rewriter"

  override final def process(input: Program): Program = {
    transform(input)
  }
}
