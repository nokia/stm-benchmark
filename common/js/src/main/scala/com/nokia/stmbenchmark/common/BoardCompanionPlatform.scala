/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.Concurrent
import fs2.Stream

private[common] abstract class BoardCompanionPlatform {

  def fromStream[F[_]](s: Stream[F, String])(implicit cF: Concurrent[F]): F[Board]
}
