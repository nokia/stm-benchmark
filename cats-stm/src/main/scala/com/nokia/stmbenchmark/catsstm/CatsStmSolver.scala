/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package catsstm

import cats.syntax.all._
import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.effect.syntax.all._

import io.github.timwspence.cats.stm.STM

import common.{ Solver, Board, Point, Route, BoolMatrix }

object CatsStmSolver {

  def apply[F[_]](txnLimit: Long, parLimit: Int, log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {
    STM.runtime[F](txnLimit).flatMap { stm =>

      import stm._

      def debug(msg: String): Txn[Unit] = {
        if (log) stm.unit.map { _ => println(msg) }
        else stm.unit
      }

      val _c = Console.make[F]

      def debugF(msg: String): F[Unit] = {
        if (log) _c.println(msg)
        else F.unit
      }

      F.pure(
        new Solver[F] {

          final override def solve(board: Board.Normalized): F[Solver.Solution] = {
            val obstructed = BoolMatrix.obstructedFromBoard(board)

            def solveOneRoute(depth: TMatrix[F, stm.type, Int], route: Route): F[List[Point]] = {
              val txn = for {
                _ <- debug(s"Solving $route")
                cost <- expand(depth, route)
                costStr <- cost.debug(debug = log)(i => f"$i%2s")
                _ <- debug("Cost after `expand`:\n" + costStr)
                solution <- solve(route, cost)
                _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solution)))
                _ <- lay(depth, solution)
              } yield solution
              stm.commit(txn)
            }

            def expand(depth: TMatrix[F, stm.type, Int], route: Route): Txn[TMatrix[F, stm.type, Int]] = {
              val startPoint = route.a
              val endPoint = route.b

              TMatrix[F, stm.type, Int](stm)(depth.height, depth.width, 0).flatMap { cost =>
                cost(startPoint.y, startPoint.x).set(1).flatMap { _ =>

                  def go(wavefront: List[Point]): Txn[List[Point]] = {
                    val mkNewWf = wavefront.foldMapA[Txn, List[Point]] { point =>
                      cost(point.y, point.x).get.flatMap { pointCost =>
                        board.adjacentPoints(point).foldMapA[Txn, List[Point]] { adjacent =>
                          if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                            // can't go in that direction
                            stm.pure(Nil)
                          } else {
                            cost(adjacent.y, adjacent.x).get.flatMap { currentCost =>
                              depth(adjacent.y, adjacent.x).get.flatMap { d =>
                                val newCost = pointCost + Board.cost(d)
                                if ((currentCost == 0) || (newCost < currentCost)) {
                                  cost(adjacent.y, adjacent.x).set(newCost).as(adjacent :: Nil)
                                } else {
                                  stm.pure(Nil)
                                }
                              }
                            }
                          }
                        }
                      }
                    }

                    mkNewWf.flatMap { newWavefront =>
                      if (newWavefront.isEmpty) {
                        stm.raiseError(new Solver.Stuck)
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
                                stm.pure(newWavefront)
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

            def solve(route: Route, cost: TMatrix[F, stm.type, Int]): Txn[List[Point]] = {
              // we're going *back* from the route end:
              val startPoint = route.b
              val endPoint = route.a
              TVar.of(List(startPoint)).flatMap { solutionVar =>
                solutionVar.get.flatMap { solution =>
                  val adjacent = board.adjacentPoints(solution.head)
                  adjacent.traverse { a =>
                    cost(a.y, a.x).get.map(a -> _)
                  }.map { costs =>
                    costs.filter(_._2 != 0).minBy(_._2)
                  }.flatMap { lowestCost =>
                    solutionVar.modify(lowestCost._1 :: _).as(lowestCost._1 == endPoint)
                  }
                }.iterateWhile(break => !break).flatMap { _ =>
                  solutionVar.get
                }
              }
            }

            def lay(depth: TMatrix[F, stm.type, Int], solution: List[Point]): Txn[Unit] = {
              solution.traverse_ { point =>
                depth(point.y, point.x).modify(_ + 1)
              }
            }

            stm.commit(TMatrix.apply[F, stm.type, Int](stm)(
              h = board.height,
              w = board.width,
              initial = 0,
            )).flatMap { depth =>
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
        }
      )
    }
  }
}
