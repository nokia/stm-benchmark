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
  testFromResource("sparselong_micro.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt", restrict = 3) // unrestricted takes approx. 12 mins

  override def afterEach(context: AfterEach): Unit = {
    printJmxInfo()
  }

  private def printJmxInfo(): Unit = {
    val mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer()
    val names = mbs.queryNames(
      new javax.management.ObjectName("dev.tauri.choam.internal.mcas.emcas:type=EmcasJmxStats*"),
      null
    )
    names.forEach { objName =>
      def getAttr(attrName: String): Option[AnyRef] =
        Either.catchOnly[javax.management.AttributeNotFoundException](mbs.getAttribute(objName, attrName)).toOption
      (
        getAttr("Commits"),
        getAttr("AvgCyclesPerCommit"),
        getAttr("CyclesDetected")
      ).mapN { (commits, avgCyclesPc, cyclesDetected) =>
        println(s"  Commits =            ${commits}")
        println(s"  AvgCyclesPerCommit = ${avgCyclesPc}")
        println(s"  CyclesDetected =     ${cyclesDetected}")
      }
    }
  }
}
