/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package rxn

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Async
import cats.effect.std.Console

import dev.tauri.choam.core.{ Rxn, AsyncReactive, RetryStrategy }
import Rxn.unsafe.Ticket

import common.{ Solver, Board, Point, Route, BoolMatrix }

/**
 * A solver using `Rxn` and a variant of (E)arly
 * (R)elease implemented with (T)ickets. Otherwise works
 * like `RxnSolver`.
 */
object ErtRxnSolver {

  // TODO: A lot of this code is duplicated with
  // TODO: `RxnSolver`; pull out the common methods.

  def apply[F[_]](
    parLimit: Int,
    log: Boolean,
    strategy: RetryStrategy = RxnSolver.spinStrategy,
  )(implicit F: Async[F], ar: AsyncReactive[F]): F[Solver[F]] = {
    val cons = Console.make[F]
    val debugStrategy = if (log) cons.println(strategy) else F.unit
    debugStrategy *> F.pure(new Solver[F] {

      private[this] val _c =
        cons

      private[this] val runConfig: RetryStrategy =
        strategy

      private[this] final def debug(msg: String): Rxn[Unit] = {
        if (log) Rxn.unit.map { _ => println(msg) }
        else Rxn.unit
      }

      private[this] final def debugF(msg: String): F[Unit] = {
        if (log) _c.println(msg)
        else F.unit
      }

      final override def solve(board: Board.Normalized): F[Solver.Solution] = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: RefMatrix[Int], route: Route): F[List[Point]] = {
          val act: Rxn[List[Point]] = for {
            _ <- if (log) debug(s"Solving $route") else Rxn.unit
            costAndTickets <- expand(depth, route)
            (cost, tickets) = costAndTickets
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            solutionList = solution.toList
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solutionList), debug = log))
            _ <- lay(route, depth, solution, tickets)
          } yield solutionList
          ar.runAsync(act, runConfig)
        }

        def expand(depth: RefMatrix[Int], route: Route): Rxn[(RefMatrix[Int], Map[Point, Ticket[Int]])] = {
          val startPoint = route.a
          val endPoint = route.b

          RefMatrix[Int](depth.height, depth.width, 0).flatMap { cost =>
            cost.set(startPoint.y, startPoint.x, 1).flatMap { _ =>

              def merge(
                acc: (Chain[Point], Map[Point, Ticket[Int]]),
                elem: (Chain[Point], Map[Point, Ticket[Int]]),
              ): (Chain[Point], Map[Point, Ticket[Int]]) = {
                (acc._1 ++ elem._1, mergeTickets(acc._2, elem._2))
              }

              def mergeTickets(
                acc: Map[Point, Ticket[Int]],
                newTickets: Map[Point, Ticket[Int]],
              ): Map[Point, Ticket[Int]] = {
                acc ++ newTickets // TODO
              }

              def go(
                wavefront: Chain[Point],
                collectedTickets: Map[Point, Ticket[Int]],
              ): Rxn[(Chain[Point], Map[Point, Ticket[Int]])] = {
                val mkNewWf = wavefront.traverse { point =>
                  cost.get(point.y, point.x).flatMap { pointCost =>
                    Chain.fromSeq(board.adjacentPoints(point)).traverse { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Rxn.pure((Chain.empty[Point], Map.empty[Point, Ticket[Int]]))
                      } else {
                        cost.get(adjacent.y, adjacent.x).flatMap { currentCost =>
                          // TODO: Is this really correct? We're relying
                          // TODO: on data read with breaking opacity.
                          // TODO: (In effect, what we have here is not
                          // TODO: really early release; it's just choosing
                          // TODO: what to write based on possibly inconsistent
                          // TODO: reads.)
                          depth.ticketRead(adjacent.y, adjacent.x).flatMap { ticket =>
                            val d = ticket.unsafePeek
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost.set(adjacent.y, adjacent.x, newCost).as(
                                (Chain(adjacent), Map(adjacent -> ticket))
                              )
                            } else {
                              Rxn.pure((Chain.empty[Point], Map.empty[Point, Ticket[Int]]))
                            }
                          }
                        }
                      }
                    }.map { ch =>
                      ch.foldLeft((Chain.empty[Point], Map.empty[Point, Ticket[Int]]))(merge)
                    }
                  }
                }.map { ch =>
                  val res = ch.foldLeft((Chain.empty[Point], Map.empty[Point, Ticket[Int]]))(merge)
                  (res._1, mergeTickets(collectedTickets, res._2))
                }

                mkNewWf.flatMap { case (newWavefront, collectedTickets) =>
                  if (newWavefront.isEmpty) {
                    Rxn.unsafe.panic(new Solver.Stuck)
                  } else {
                    cost.get(endPoint.y, endPoint.x).flatMap { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost.get(marked.y, marked.x)
                        }.flatMap { newCosts =>
                          val minimumNewCost = newCosts.minimumOption.get // TODO: partial function
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            Rxn.pure((newWavefront, collectedTickets))
                          } else {
                            // continue with the new wavefront:
                            go(newWavefront, collectedTickets)
                          }
                        }
                      } else {
                        // continue with the new wavefront:
                        go(newWavefront, collectedTickets)
                      }
                    }
                  }
                }
              }

              go(Chain(startPoint), Map.empty).map { res =>
                (cost, res._2)
              }
            }
          }
        }

        def solve(route: Route, cost: RefMatrix[Int]): Rxn[NonEmptyChain[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          Rxn.monadInstance.iterateWhileM(NonEmptyChain(startPoint)) { solution =>
            val adjacent = board.adjacentPoints(solution.head)
            adjacent.traverse { a =>
              cost.get(a.y, a.x).map(a -> _)
            }.map { costs =>
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
              lowestCost._1 +: solution
            }
          } (p = { solution => solution.head != endPoint })
        }

        def lay(
          route: Route,
          depth: RefMatrix[Int],
          solution: NonEmptyChain[Point],
          collectedTickets: Map[Point, Ticket[Int]],
        ): Rxn[Unit] = {
          solution.traverse_ { point =>
            if (point == route.a) {
              // we never create a ticket for the starting point,
              // so just use a regular update:
              depth.update(point.y, point.x)(_ + 1)
            } else {
              val ticket = collectedTickets.apply(point)
              ticket.unsafeSet(ticket.unsafePeek + 1)
            }
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
