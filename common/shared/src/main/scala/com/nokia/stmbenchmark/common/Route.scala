/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

/** A route to solve (from `a` to `b`) */
final case class Route(a: Point, b: Point) {
  require(a != b)
}

object Route {
  implicit val ordering: Ordering[Route] =
    Ordering.by[Route, (Point, Point)] { r => (r.a, r.b) }
}
