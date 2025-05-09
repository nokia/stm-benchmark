/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

/** A point on a `Board` */
final case class Point(x: Int, y: Int) {

  /** Unsafe because the result(s) may not be on the board */
  final def unsafeAdjacent: List[Point] = {
    val lb = List.newBuilder[Point]
    if (x > 0) {
      lb += Point(x - 1, y)
    }
    if (y > 0) {
      lb += Point(x, y - 1)
    }
    lb += Point(x + 1, y)
    lb += Point(x, y + 1)
    lb.result()
  }

  final def manhattanDistance(that: Point): Int = {
    java.lang.Math.abs(this.x - that.x) + java.lang.Math.abs(this.y - that.y)
  }
}

object Point {
  implicit val ordering: Ordering[Point] =
    Ordering.by[Point, (Int, Int)] { p => (p.x, p.y) }
}
