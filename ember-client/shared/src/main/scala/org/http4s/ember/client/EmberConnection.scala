/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.client

import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.effect.std.Hotswap
import cats.syntax.all._
import fs2.Chunk

private[ember] final case class EmberConnection[F[_]](
    keySocket: RequestKeySocket[F],
    chunkSize: Int,
    shutdown: F[Unit],
    nextBytes: Ref[F, Array[Byte]],
    hotRead: Hotswap[F, Deferred[F, Either[Throwable, Option[Chunk[Byte]]]]],
    nextRead: Ref[F, Deferred[F, Either[Throwable, Option[Chunk[Byte]]]]],
)(implicit F: Concurrent[F]) {
  def isValid: F[Boolean] = {
    val isOpen = keySocket.socket.isOpen
    val isEof = nextRead.get.flatMap(_.tryGet).map {
      case Some(result) => result.exists(_.isDefined) // if Left or None this socket is dead
      case None => false // no read yet, which is good!
    }
    (isOpen, isEof).mapN((open, eof) => open && !eof)
  }

  def startNextRead: F[Unit] =
    hotRead.swap {
        Resource.eval(F.deferred[Either[Throwable, Option[Chunk[Byte]]]]).flatTap { result =>
          val read = keySocket.socket.read(chunkSize)
          F.background(read.attempt.flatMap(result.complete(_)).void.voidError)
        }
      }
      .flatMap(nextRead.set(_))

  def cleanup: F[Unit] =
    nextBytes.set(Array.emptyByteArray) >>
      keySocket.socket.endOfInput.attempt.void >>
      keySocket.socket.endOfOutput.attempt.void >>
      shutdown.attempt.void
}

private[ember] object EmberConnection {
  def apply[F[_]](
      keySocketResource: Resource[F, RequestKeySocket[F]],
      chunkSize: Int,
  )(implicit F: Concurrent[F]): Resource[F, EmberConnection[F]] =
    (
      Resource.eval(keySocketResource.allocated),
      Resource.eval(F.ref(Array.emptyByteArray)),
      Hotswap.create[F, Deferred[F, Either[Throwable, Option[Chunk[Byte]]]]],
      Resource.eval(
        F.deferred[Either[Throwable, Option[Chunk[Byte]]]]
          .flatTap(_.complete(Right(Some(Chunk.empty))))
          .flatMap(F.ref(_))
      ),
    ).flatMapN { case ((keySocket, release), nextBytes, hotRead, nextRead) =>
      Resource.make(F.pure(EmberConnection(keySocket, chunkSize, release, nextBytes, hotRead, nextRead)))(
        _.cleanup
      )
    }
}
