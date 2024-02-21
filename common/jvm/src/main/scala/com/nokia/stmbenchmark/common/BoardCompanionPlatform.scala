/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.{ Concurrent, Sync }

import fs2.Stream
import fs2.text

private[common] abstract class BoardCompanionPlatform {

  def fromStream[F[_]](s: Stream[F, String])(implicit cF: Concurrent[F]): F[Board]

  def fromResource[F[_]](path: String)(implicit sF: Sync[F], cF: Concurrent[F]): F[Board] = {
    val stream = fs2.io.readClassLoaderResource(path, classLoader = classOf[Board].getClassLoader()).through(text.utf8.decode)
    fromStream(stream)
  }
}
