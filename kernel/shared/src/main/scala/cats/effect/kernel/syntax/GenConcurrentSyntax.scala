/*
 * Copyright 2020 Typelevel
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

package cats.effect.kernel.syntax

import cats.effect.kernel.{Concurrent, GenConcurrent}
import cats.Traverse

trait GenConcurrentSyntax {
  implicit def genConcurrentOps[F[_], E, A](wrapped: F[A]): GenConcurrentOps[F, E, A] =
    new GenConcurrentOps(wrapped)

  implicit def concurrentParTraverseOps[F[_], T[_], A, B](
      wrapped: T[A]): ConcurrentParTraverseNOps[F, T, A, B] =
    new ConcurrentParTraverseNOps(wrapped)

  implicit def concurrentParSequenceOps[F[_], T[_], A](
      wrapped: T[F[A]]): ConcurrentParSequenceNOps[F, T, A] =
    new ConcurrentParSequenceNOps(wrapped)
}

final class GenConcurrentOps[F[_], E, A] private[syntax] (private[syntax] val wrapped: F[A])
    extends AnyVal {
  def memoize(implicit F: GenConcurrent[F, E]): F[F[A]] =
    F.memoize(wrapped)
}

final class ConcurrentParTraverseNOps[F[_], T[_], A, B] private[syntax] (
    private[syntax] val wrapped: T[A])
    extends AnyVal {
  def parTraverseN(n: Int)(f: A => F[B])(implicit F: Concurrent[F], T: Traverse[T]): F[T[B]] =
    F.parTraverseN(n)(wrapped)(f)
}

final class ConcurrentParSequenceNOps[F[_], T[_], A] private[syntax] (
    private[syntax] val wrapped: T[F[A]])
    extends AnyVal {
  def parSequenceN(n: Int)(implicit F: Concurrent[F], T: Traverse[T]): F[T[A]] =
    F.parSequenceN(n)(wrapped)
}
