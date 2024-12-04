/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package sequential

import cats.effect.Async
import cats.syntax.all._

import common.{ Solver, Board, BoolMatrix, Route, Point }

object SequentialSolver {

  def apply[F[_]](log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {
    F.pure(new Solver[F] {

      private[this] def debug(msg: String): F[Unit] = {
        if (log) F.delay { println(msg) }
        else F.unit
      }

      override def solve(board: Board.Normalized): F[Solver.Solution] = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: Matrix[F, Int], route: Route): F[List[Point]] = {
          for {
            _ <- if (log) debug(s"Solving $route") else F.unit
            cost <- expand(depth, route)
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log))
            _ <- lay(depth, solution)
          } yield solution
        }

        def expand(depth: Matrix[F, Int], route: Route): F[Matrix[F, Int]] = {
          val startPoint = route.a
          val endPoint = route.b

          Matrix[F, Int](depth.height, depth.width, 0).flatMap { cost =>
            cost(startPoint.y, startPoint.x).set(1).flatMap { _ =>

              def go(wavefront: List[Point]): F[List[Point]] = {
                val mkNewWf: F[List[Point]] = wavefront.foldMapA[F, List[Point]] { point =>
                  cost(point.y, point.x).get.flatMap { pointCost =>
                    board.adjacentPoints(point).foldMapA[F, List[Point]] { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        F.pure(Nil)
                      } else {
                        cost(adjacent.y, adjacent.x).get.flatMap { currentCost =>
                          depth(adjacent.y, adjacent.x).get.flatMap { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost(adjacent.y, adjacent.x).set(newCost).as(adjacent :: Nil)
                            } else {
                              F.pure(Nil)
                            }
                          }
                        }
                      }
                    }
                  }
                }

                mkNewWf.flatMap { newWavefront =>
                  if (newWavefront.isEmpty) {
                    F.raiseError(new Solver.Stuck)
                  } else {
                    cost(endPoint.y, endPoint.x).get.flatMap { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost(marked.y, marked.x).get
                        }.flatMap { newCosts =>
                          val minimumNewCost = newCosts.min
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            F.pure(newWavefront)
                          } else {
                            // continue with the new wavefront:
                            go(newWavefront)
                          }
                        }
                      } else {
                        // continue with the new wavefront:
                        go(newWavefront)
                      }
                    }
                  }
                }
              }

              go(List(startPoint)).as(cost)
            }
          }
        }

        def solve(route: Route, cost: Matrix[F, Int]): F[List[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          F.ref(List(startPoint)).flatMap { solutionRef =>
            solutionRef.get.flatMap { solution =>
              val adjacent = board.adjacentPoints(solution.head)
              adjacent.traverse { a =>
                cost(a.y, a.x).get.map(a -> _)
              }.map { costs =>
                costs.filter(_._2 != 0).minBy(_._2)
              }.flatMap { lowestCost =>
                solutionRef.update(lowestCost._1 :: _).as(lowestCost._1 == endPoint)
              }
            }.iterateWhile(break => !break).flatMap { _ =>
              solutionRef.get
            }
          }
        }

        def lay(depth: Matrix[F, Int], solution: List[Point]): F[Unit] = {
          solution.traverse_ { point =>
            depth(point.y, point.x).update(_ + 1)
          }
        }

        Matrix.apply[F, Int](
          h = board.height,
          w = board.width,
          initial = 0,
        ).flatMap { depth =>
          val solveSequentially = board.routes.traverse { route =>
            solveOneRoute(depth, route).map { solution =>
              route -> solution
            }
          }
          solveSequentially.flatMap { solutions =>
            val solution = Map(solutions: _*)
            debug("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
          }
        }
      }
    })
  }
}
