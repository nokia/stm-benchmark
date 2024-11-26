/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import java.util.Arrays

import scala.util.Random

import cats.syntax.all._
import cats.effect.kernel.{ Concurrent }

import fs2.Stream
import fs2.text
import fs2.io.file.{ Files, Path }

sealed abstract class AbstractBoard(
  height: Int,
  width: Int,
) {

  def isPointOnBoard(p: Point): Boolean = {
    (p.x >= 0) && (p.y >= 0) && (p.x < width) && (p.y < height)
  }

  def isRouteOnBoard(r: Route): Boolean = {
    isPointOnBoard(r.a) && isPointOnBoard(r.b)
  }

  def arePointsAdjacent(a: Point, b: Point): Boolean = {
    val aAdj = adjacentPoints(a)
    if (aAdj.contains(b)) {
      val bAdj = adjacentPoints(b)
      bAdj.contains(a)
    } else {
      false
    }
  }

  def adjacentPoints(point: Point): List[Point] = {
    point.unsafeAdjacent.filter { p =>
      isPointOnBoard(p)
    }
  }

  def isSolutionValid(routes: List[Route], solution: Map[Route, List[Point]]): Boolean = {
    (routes.size == solution.size) && routes.traverse[Option, Unit] { r =>
      solution.get(r) flatMap {
        case path @ (fst :: _ :: _) =>
          if ((fst == r.a) && (path.last == r.b)) {
            // check if the solution path is continous:
            val continous = path.sliding(2).forall {
              case List(prev, next) =>
                arePointsAdjacent(prev, next)
              case _ =>
                impossible("List#sliding")
            }
            if (continous) Some(()) else None
          } else {
            None
          }
        case _ =>
          None
      }
    }.isDefined
  }

  private[this] final val EMPTY = '⋅'
  private[this] final val LETTERS: Array[Char] = {
    ('A' to 'Z').concat('a' to 'z').toArray
  }

  def debugSolution(solution: Map[Route, List[Point]], debug: Boolean): String = {
    if (debug) {
      val arr = new Array[Char](width * height)
      Arrays.fill(arr, EMPTY)
      val writePadLetters = solution.size <= LETTERS.size
      for (((route, s), routeIdx) <- solution.zipWithIndex) {
        for (p <- s) {
          val idx = (p.y * width) + p.x
          arr(idx) = arr(idx) match {
            case EMPTY => '1'
            case ch => (ch + 1).toChar
          }
        }
        val aIdx = (route.a.y * width) + route.a.x
        val bIdx = (route.b.y * width) + route.b.x
        val padChar = if (writePadLetters) LETTERS(routeIdx) else 'O'
        arr(aIdx) = padChar
        arr(bIdx) = padChar
      }

      val sb = new java.lang.StringBuilder()
      for (row <- 0 until height) {
        for (col <- 0 until width) {
          sb.append(arr((row * width) + col))
        }
        sb.append('\n')
      }
      sb.toString()
    } else {
      ""
    }
  }
}

/**
 * A circuit board to solve: some pads (wires
 * mustn't cross pads), and routes to lay down.
 */
final case class Board(
  height: Int,
  width: Int,
  pads: Set[Point],
  routes: Set[Route],
) extends AbstractBoard(height, width) {

  def isSolutionValid(solution: Map[Route, List[Point]]): Boolean = {
    this.isSolutionValid(routes.toList, solution)
  }

  def normalize(): Board.Normalized = {
    this.normalize(-8309089642316774578L)
  }

  def normalize(seed: Long): Board.Normalized = {
    // normalize the board, by (pseudo-randomly, based
    // on `seed`) shuffling routes to reduce obvious
    // conflicts; also flip some of the routes start
    // and end points (similarly pseudo-randomly):
    val rnd = new Random(seed)
    Board.Normalized(
      height = this.height,
      width = this.width,
      pads = this.pads.toList.sorted,
      routes = rnd.shuffle(this.routes.toList.sorted).map { r =>
        if (rnd.nextBoolean()) Route(a = r.b, b = r.a)
        else r
      },
      restricted = 0,
    )
  }
}

object Board extends BoardCompanionPlatform {

  final class FileFormatException(msg: String)
    extends Exception(msg)

  final case class Normalized(
    height: Int,
    width: Int,
    pads: List[Point],
    routes: List[Route],
    restricted: Int,
  ) extends AbstractBoard(height = height, width = width) {

    def isSolutionValid(solution: Map[Route, List[Point]]): Boolean = {
      this.isSolutionValid(routes, solution)
    }

    def restrict(rshift: Int): Normalized = {
      if (rshift == 0) {
        this
      } else {
        require(rshift > 0)
        require(rshift < 32)
        val n = this.routes.size
        if (n == 0) {
          // restricting an empty board is effectively a NOP:
          this.copy(restricted = this.restricted + rshift)
        } else {
          val k = n >> rshift
          require(k > 0)
          require(k < n)
          this.copy(routes = this.routes.take(k), restricted = this.restricted + rshift)
        }
      }
    }
  }

  def empty(h: Int, w: Int): Board =
    Board(h, w, Set.empty, Set.empty)

  def fromFile[F[_]](path: String)(implicit fF: Files[F], cF: Concurrent[F]): F[Board] = {
    val stream = fF
      .readAll(Path(path))
      .through(text.utf8.decode)
    fromStream(stream)
  }

  final override def fromStream[F[_]](s: Stream[F, String])(implicit cF: Concurrent[F]): F[Board] = {
    val stream = s
      .through(text.lines)
      .evalMapFilter[F, (String, Seq[Int])] { line =>
        line.split(' ') match {
          case Array(opcode, rest*) =>
            if (opcode != "#") {
              rest.traverse { tok =>
                cF.catchNonFatal(tok.toInt).handleErrorWith {
                  case _: NumberFormatException =>
                    cF.raiseError(new FileFormatException(s"not an Int: $tok"))
                  case ex =>
                    cF.raiseError(ex)
                }
              }.map { r => Some(opcode -> r) }
            } else {
              cF.pure(None)
            }
          case _ =>
            cF.raiseError(new FileFormatException(s"invalid line: $line"))
        }
      }
      .takeWhile { case (opcode, _) => opcode != "E" }
      .evalScan(Board.empty(0, 0)) { (board: Board, numbers: (String, Seq[Int])) =>
        numbers match {
          case ("B", Seq(w, h)) => // board size
            if ((board.height != 0) || (board.width != 0)) {
              cF.raiseError(new FileFormatException("2 B lines"))
            } else {
              if (w < 0) cF.raiseError(new FileFormatException("w < 0"))
              else if (h < 0) cF.raiseError(new FileFormatException("h < 0"))
              else cF.pure(board.copy(height = h, width = w))
            }
          case ("P", Seq(x, y)) => // a pad
            val p = Point(x, y)
            if (board.isPointOnBoard(p)) {
              cF.pure(board.copy(pads = board.pads + p))
            } else {
              cF.raiseError(new FileFormatException(s"pad not on board: $p"))
            }
          case ("J", Seq(ax, ay, bx, by)) => // a route
            val a = Point(ax, ay)
            val b = Point(bx, by)
            val r = Route(a, b)
            if (a != b) {
              if (board.isRouteOnBoard(r)) {
                if (board.pads.contains(r.a) && board.pads.contains(r.b)) {
                  cF.pure(board.copy(routes = board.routes + r))
                } else {
                  cF.raiseError(new FileFormatException(s"route doesn't connect two pads: $r"))
                }
              } else {
                cF.raiseError(new FileFormatException(s"route not on board: $r"))
              }
            } else {
              cF.raiseError(new FileFormatException(s"invalid route (to itself): $r"))
            }
          case line =>
            cF.raiseError(new FileFormatException(s"malformed line: $line"))
        }
      }

    stream.compile.lastOrError.flatMap { result =>
      if (result == Board.empty(0, 0)) {
        cF.raiseError(new FileFormatException("empty board"))
      } else {
        cF.pure(result)
      }
    }
  }

  /** Cost for laying routes over each other */
  def cost(depth: Int): Int = {
    require(depth >= 0)
    Math.pow(2.0, depth.toDouble).toInt
  }

  def debugSolutionStats(solution: Solver.Solution, debug: Boolean, indent: String): String = {
    if (debug) {
      val sb = new java.lang.StringBuilder()
      sb.append(indent)
      sb.append("routes:     ")
      sb.append(solution.routes.size)
      sb.append('\n')
      sb.append(indent)
      sb.append("total cost: ")
      sb.append(solution.totalCost)
      sb.append('\n')
      sb.append(indent)
      sb.append("max. depth: ")
      sb.append(solution.maxDepth)
      sb.append('\n')
      sb.toString()
    } else {
      ""
    }
  }
}
