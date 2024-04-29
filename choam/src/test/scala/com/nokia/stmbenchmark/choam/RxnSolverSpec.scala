/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import cats.syntax.all._
import cats.effect.IO

import common.JvmCeIoSolverSpec
import common.Solver

final class RxnSolverSpec extends JvmCeIoSolverSpec {

  override protected def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      RxnSolver[IO](parLimit = numCpu, log = false, strategy = RxnSolver.sleepStrategy)
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt", restrict = 3) // unrestricted takes approx. 12 mins
  testFromResource("memboard.txt", restrict = 2) // unrestricted takes approx. 6 mins

  override def afterEach(context: AfterEach): Unit = {
    printJmxInfo()
  }

  private def printJmxInfo(): Unit = {
    val mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer()
    val names = mbs.queryNames(
      new javax.management.ObjectName("dev.tauri.choam.stats:type=EmcasJmxStats*"),
      null
    )

    names.forEach { objName =>

      def getAttr(attrName: String): Option[(String, AnyRef)] = {
        Either
          .catchOnly[javax.management.AttributeNotFoundException](mbs.getAttribute(objName, attrName))
          .toOption
          .map(attrName -> _)
      }

      val attrs = List(
        "Commits",
        "McasAttempts",
        "CyclesDetected",
        "AvgCyclesPerMcasAttempt",
      ).sorted

      for ((name, value) <- attrs.map(getAttr).collect { case Some(kv) => kv }) {
        println(s"  ${name}: ${value}")
      }

      mbs.invoke(objName, "checkConsistency", Array.empty[AnyRef], Array.empty[String]) match {
        case null =>
          // OK
        case errMsg =>
          println(errMsg.toString)
      }
    }
  }
}
