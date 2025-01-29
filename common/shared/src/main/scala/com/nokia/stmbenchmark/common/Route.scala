/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

/** A route to solve (from `a` to `b`) */
final case class Route(a: Point, b: Point) {

  require(a != b)

  /** The theoretically minimal length of the route */
  final def idealLength: Int = {
    a manhattanDistance b
  }
}

object Route {

  val orderByCoordinates: Ordering[Route] =
    Ordering.by[Route, (Point, Point)] { r => (r.a, r.b) }

  val orderByLength: Ordering[Route] =
    Ordering.by[Route, Int] { _.idealLength }
}
