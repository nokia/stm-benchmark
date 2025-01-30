/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import java.util.concurrent.ThreadLocalRandom

import cats.syntax.all._
import cats.effect.IO

import fs2.Stream

import munit.{ CatsEffectSuite, Location }

final class BoardSpec extends CatsEffectSuite {

  def assertF(cond: Boolean, clue: => Any = "assertion failed")(implicit loc: Location): IO[Unit] = {
    IO { this.assert(cond, clue)(loc) }
  }

  private val expMinimal = Board(
    10,
    10,
    pads = Set(Point(2, 2), Point(7, 2), Point(2, 7), Point(7, 7)),
    routes = Set(Route(Point(2, 2), Point(7, 7)), Route(Point(7, 2), Point(2, 7))),
  )

  test("Board.fromStream") {
    val s = Stream[IO, String](
      List(
        "B 10 10",
        "P 2 2",
        "P 7 2",
        "P 2 7",
        "P 7 7",
        "J 2 2 7 7",
        "J 7 2 2 7",
        "E",
        "",
      ).mkString("\n")
    )

    Board.fromStream(s).flatMap { b =>
      assertF(b == expMinimal)
    }
  }

  test("Board.fromStream comment lines") {
    val s = Stream[IO, String](
      List(
        "# this is a minimal board: ",
        "B 10 10",
        "P 2 2",
        "P 7 2",
        "P 2 7",
        "P 7 7",
        "# routes:",
        "J 2 2 7 7",
        "J 7 2 2 7",
        "E",
        "",
        "# trailing comment line",
      ).mkString("\n")
    )

    Board.fromStream(s).flatMap { b =>
      assertF(b == expMinimal)
    }
  }

  test("Board.fromStream error handling") {
    val files = List(
      List(
        "B 10 e",
      ),
      List(
        "23 45",
      ),
      List(
        "B 10 10",
        "P 10 2"
      ),
      List(
        "B 0 0",
        "P 0 0"
      ),
    )

    val tests = for (file <- files) yield {
      val s = Stream[IO, String](file.mkString("\n"))
      Board.fromStream(s).attempt.flatMap {
        case Left(ex) =>
          assertF(ex.isInstanceOf[Board.FileFormatException])
        case Right(b) =>
          IO { this.fail(s"unexpected success: ${b}") }
      }
    }

    tests.sequence.void
  }

  test("Board#normalize (small)") {
    val pads = Set(Point(0, 0), Point(2, 2), Point(5, 5), Point(9, 9))
    val b = Board(
      10,
      10,
      pads = pads,
      routes = Set(Route(Point(0, 0), Point(2, 2)), Route(Point(5, 5), Point(9, 9))),
    )
    val n = b.normalize(42L)
    val exp = Board.Normalized(
      10,
      10,
      pads = pads.toList.sorted,
      routes = List(Route(Point(0, 0), Point(2, 2)), Route(Point(9, 9), Point(5, 5))),
      restricted = 0,
    )
    assertEquals(n, exp)
  }

  test("Board#normalize (bigger)") {
    def flipToOrdered(r: Route): Route = {
      if (Point.ordering.lteq(r.a, r.b)) r
      else r.flipped
    }
    val pads = Set(
      Point(0, 0), Point(2, 2),
      Point(5, 5), Point(9, 9),
      Point(0, 9), Point(2, 7),
      Point(5, 4), Point(9, 0),
    )
    val routes = Set(
      Route(Point(0, 0), Point(2, 2)),
      Route(Point(5, 5), Point(9, 9)),
      Route(Point(0, 9), Point(2, 7)),
      Route(Point(5, 4), Point(9, 0)),
    )
    val b = Board(
      10,
      10,
      pads = pads,
      routes = routes,
    )
    val n = b.normalize(ThreadLocalRandom.current().nextLong())
    assertEquals(n.height, b.height)
    assertEquals(n.width, b.width)
    assertEquals(n.restricted, 0)
    assertEquals(n.pads, pads.toList.sorted)
    assertEquals(n.routes.size, 4)
    // shorter routes first:
    assertEquals(n.routes.take(2).map(_.idealLength).toSet, Set(4))
    assertEquals(
      n.routes.take(2).map(flipToOrdered).toSet,
      Set(Route(Point(0, 0), Point(2, 2)), Route(Point(0, 9), Point(2, 7))).map(flipToOrdered),
    )
    // longer ones later:
    assertEquals(n.routes.drop(2).map(_.idealLength).toSet, Set(8))
    assertEquals(
      n.routes.drop(2).map(flipToOrdered).toSet,
      Set(Route(Point(5, 5), Point(9, 9)), Route(Point(5, 4), Point(9, 0))).map(flipToOrdered),
    )
  }

  test("Board.Normalized#restrict") {
    val b = Board.Normalized(
      10,
      10,
      pads = List(Point(2, 2), Point(7, 2), Point(2, 7), Point(7, 7)).sorted,
      routes = List(Route(Point(2, 2), Point(7, 7)), Route(Point(7, 2), Point(2, 7))),
      restricted = 0,
    )
    val r = b.restrict(1, 42L) // halve routes
    val exp = Board.Normalized(
      10,
      10,
      pads = List(Point(2, 2), Point(7, 2), Point(2, 7), Point(7, 7)).sorted,
      routes = List(Route(Point(2, 2), Point(7, 7))),
      restricted = 1,
    )
    assertEquals(r, exp)
  }

  test("Point#manhattanDistance") {
    assertEquals(Point(0, 0) manhattanDistance Point(2, 2), 4)
    assertEquals(Point(0, 0) manhattanDistance Point(0, 2), 2)
    assertEquals(Point(0, 0) manhattanDistance Point(2, 0), 2)
    assertEquals(Point(0, 0) manhattanDistance Point(2, 3), 5)
    assertEquals(Point(0, 0) manhattanDistance Point(-2, 3), 5)
    assertEquals(Point(0, 0) manhattanDistance Point(-2, -3), 5)
  }

  test("Route#idealLength") {
    assertEquals(Route(Point(2, 2), Point(0, 0)).idealLength, 4)
    assertEquals(Route(Point(-2, 3), Point(0, 0)).idealLength, 5)
  }
}
