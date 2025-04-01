/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Async
import cats.effect.std.Console

import dev.tauri.choam.{ Rxn, Axn }
import dev.tauri.choam.core.RetryStrategy
import dev.tauri.choam.async.AsyncReactive

import common.{ Solver, Board, Point, Route, BoolMatrix }

/** A solver using `Rxn` for parallelization */
object RxnSolver {

  private[stmbenchmark] val spinStrategy: RetryStrategy.Spin =
    RetryStrategy.Default

  private[stmbenchmark] val cedeStrategy: RetryStrategy =
    spinStrategy.withCede(true)

  private[stmbenchmark] val sleepStrategy: RetryStrategy =
    cedeStrategy.withSleep(true)

  def apply[F[_]](
    parLimit: Int,
    log: Boolean,
    strategy: RetryStrategy = spinStrategy,
  )(implicit F: Async[F], ar: AsyncReactive[F]): F[Solver[F]] = {
    val cons = Console.make[F]
    val debugStrategy = if (log) cons.println(strategy) else F.unit
    debugStrategy *> F.pure(new Solver[F] {

      private[this] val _c =
        cons

      private[this] val runConfig: RetryStrategy =
        strategy

      private[this] final def debug(msg: String): Axn[Unit] = {
        if (log) Axn.unit.map { _ => println(msg) }
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
            _ <- if (log) debug(s"Solving $route") else Axn.unit
            cost <- expand(depth, route)
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            solutionList = solution.toList
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solutionList), debug = log))
            _ <- lay(depth, solution)
          } yield solutionList
          ar.applyAsync(act, null, runConfig)
        }

        def expand(depth: RefMatrix[Int], route: Route): Axn[RefMatrix[Int]] = {
          val startPoint = route.a
          val endPoint = route.b

          RefMatrix[Int](depth.height, depth.width, 0).flatMapF { cost =>
            cost(startPoint.y, startPoint.x).set1(1).flatMapF { _ =>

              def go(wavefront: Chain[Point]): Axn[Chain[Point]] = {
                val mkNewWf = wavefront.traverse { point =>
                  cost(point.y, point.x).get.flatMapF { pointCost =>
                    Chain.fromSeq(board.adjacentPoints(point)).traverse { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Rxn.pure(Chain.empty)
                      } else {
                        cost(adjacent.y, adjacent.x).get.flatMapF { currentCost =>
                          depth(adjacent.y, adjacent.x).get.flatMapF { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost(adjacent.y, adjacent.x).set1(newCost).as(Chain(adjacent))
                            } else {
                              Rxn.pure(Chain.empty)
                            }
                          }
                        }
                      }
                    }.map(_.flatten)
                  }
                }.map(_.flatten)

                mkNewWf.flatMap { newWavefront =>
                  if (newWavefront.isEmpty) {
                    Axn.panic(new Solver.Stuck)
                  } else {
                    cost(endPoint.y, endPoint.x).get.flatMapF { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost(marked.y, marked.x).get
                        }.flatMapF { newCosts =>
                          val minimumNewCost = newCosts.minimumOption.get // TODO: partial function
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

              go(Chain(startPoint)).as(cost)
            }
          }
        }

        def solve(route: Route, cost: RefMatrix[Int]): Axn[NonEmptyChain[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          Rxn.monadInstance.iterateWhileM(NonEmptyChain(startPoint)) { solution =>
            val adjacent = board.adjacentPoints(solution.head)
            adjacent.traverse { a =>
              cost(a.y, a.x).get.map(a -> _)
            }.map { costs =>
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
              lowestCost._1 +: solution
            }
          } (p = { solution => solution.head != endPoint })
        }

        def lay(depth: RefMatrix[Int], solution: NonEmptyChain[Point]): Axn[Unit] = {
          solution.traverse_ { point =>
            depth(point.y, point.x).update(_ + 1)
          }
        }

        RefMatrix.apply(
          h = board.height,
          w = board.width,
          initial = 0,
        ).run[F].flatMap { depth =>
          val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
          val solveOne = { (route: Route) =>
            solveOneRoute(depth, route).map(route -> _)
          }
          val solveAll = if (pl == 1) {
            board.routes.traverse(solveOne)
          } else {
            board.routes.parTraverseN(pl)(solveOne)
          }
          solveAll.flatMap { solutions =>
            val solution = Map(solutions: _*)
            debugF("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
          }
        }
      }
    })
  }
}
