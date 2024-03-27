/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

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

  test("Board.Normalized#restrict") {
    val b = Board.Normalized(
      10,
      10,
      pads = List(Point(2, 2), Point(7, 2), Point(2, 7), Point(7, 7)).sorted,
      routes = List(Route(Point(2, 2), Point(7, 7)), Route(Point(7, 2), Point(2, 7))),
      restricted = 0,
    )
    val r = b.restrict(1) // halve routes
    val exp = Board.Normalized(
      10,
      10,
      pads = List(Point(2, 2), Point(7, 2), Point(2, 7), Point(7, 7)).sorted,
      routes = List(Route(Point(2, 2), Point(7, 7))),
      restricted = 1,
    )
    assertEquals(r, exp)
  }
}
