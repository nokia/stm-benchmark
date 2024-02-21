/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Async
import cats.effect.std.Console

import dev.tauri.choam.{ Rxn, Axn, Reactive, Ref }

import common.{ Solver, Board, Point, Route, BoolMatrix }

object RxnSolver {

  def apply[F[_]](parLimit: Int, log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {
    F.pure(new Solver[F] {

      private[this] implicit val reactive: Reactive[F] =
        Reactive.forSync[F]

      private[this] val _c =
        Console.make[F]

      private[this] val runConfig: Rxn.Strategy.Spin =
        Rxn.Strategy.Default

      private[this] final def debug(msg: String): Axn[Unit] = {
        if (log) Axn.unsafe.delay { println(msg) }
        else Rxn.unit
      }

      private[this] final def debugF(msg: String): F[Unit] = {
        if (log) _c.println(msg)
        else F.unit
      }

      final override def solve(board: Board.Normalized): F[Solver.Solution] = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: RefMatrix[Int], route: Route): F[List[Point]] = {
          val act: Axn[List[Point]] = for {
            _ <- debug(s"Solving $route")
            cost <- expand(depth, route)
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solution)))
            _ <- lay(depth, solution)
          } yield solution
          reactive.apply(act, null, runConfig)
        }

        def expand(depth: RefMatrix[Int], route: Route): Axn[RefMatrix[Int]] = {
          val startPoint = route.a
          val endPoint = route.b

          RefMatrix[Int](depth.height, depth.width, 0).flatMapF { cost =>
            cost(startPoint.y, startPoint.x).set.provide(1).flatMapF { _ =>

              def go(wavefront: List[Point]): Axn[List[Point]] = {
                val mkNewWf = wavefront.foldMapA[Axn, List[Point]] { point =>
                  cost(point.y, point.x).get.flatMapF { pointCost =>
                    board.adjacentPoints(point).foldMapA[Axn, List[Point]] { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Rxn.pure(Nil)
                      } else {
                        cost(adjacent.y, adjacent.x).get.flatMapF { currentCost =>
                          depth(adjacent.y, adjacent.x).get.flatMapF { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost(adjacent.y, adjacent.x).set.provide(newCost).as(adjacent :: Nil)
                            } else {
                              Rxn.pure(Nil)
                            }
                          }
                        }
                      }
                    }
                  }
                }

                mkNewWf.flatMap { newWavefront =>
                  if (newWavefront.isEmpty) {
                    Axn.unsafe.delay { throw new Solver.Stuck }
                  } else {
                    cost(endPoint.y, endPoint.x).get.flatMapF { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost(marked.y, marked.x).get
                        }.flatMapF { newCosts =>
                          val minimumNewCost = newCosts.min
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            Rxn.pure(newWavefront)
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

        def solve(route: Route, cost: RefMatrix[Int]): Axn[List[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          Ref.unpadded(List(startPoint)).flatMap { solutionRef =>
            solutionRef.get.flatMapF { solution =>
              val adjacent = board.adjacentPoints(solution.head)
              adjacent.traverse { a =>
                cost(a.y, a.x).get.map(a -> _)
              }.map { costs =>
                costs.filter(_._2 != 0).minBy(_._2)
              }.flatMapF { lowestCost =>
                solutionRef.update(lowestCost._1 :: _).as(lowestCost._1 == endPoint)
              }
            }.iterateWhile(break => !break).flatMap { _ =>
              solutionRef.get
            }
          }
        }

        def lay(depth: RefMatrix[Int], solution: List[Point]): Axn[Unit] = {
          solution.traverse_ { point =>
            depth(point.y, point.x).update(_ + 1)
          }
        }

        RefMatrix.apply(h = board.height, w = board.width, initial = 0).run[F].flatMap { depth =>
          val solveInParallel = board.routes.parTraverseN(parLimit) { route =>
            solveOneRoute(depth, route).map { solution =>
              route -> solution
            }
          }
          solveInParallel.flatMap { solutions =>
            val solution = Map(solutions: _*)
            debugF("Full solution:\n" + board.debugSolution(solution)).as(Solver.Solution(solution))
          }
        }
      }
    })
  }
}
