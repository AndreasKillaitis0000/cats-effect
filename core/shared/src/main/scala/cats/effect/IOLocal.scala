/*
 * Copyright 2020-2022 Typelevel
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

package cats.effect

import cats.syntax.functor._

/**
 * [[IOLocal]] provides a handy way of manipulating a context on different scopes.
 *
 * In some scenarios, [[IOLocal]] can be considered as an alternative to [[cats.data.Kleisli]].
 *
 * [[IOLocal]] should not be treated as [[cats.effect.kernel.Ref Ref]], since the former abides
 * different laws.
 *
 * Once a fiber is forked, for example by `Spawn[F].start`, the forked fiber manipulates the
 * copy of the parent's context. For example, two forked fibers will never see each other's
 * modifications to the same [[IOLocal]], each fiber will only see its own modifications.
 *
 * ===Operations on [[IOLocal]] are visible to the fiber===
 *
 * {{{
 * ┌────────────┐               ┌────────────┐               ┌────────────┐
 * │  Fiber A   │ update(_ + 1) │  Fiber A   │ update(_ + 1) │  Fiber A   │
 * │ (local 42) │──────────────►│ (local 43) │──────────────►│ (local 44) │
 * └────────────┘               └────────────┘               └────────────┘
 * }}}
 *
 * {{{
 *  def inc(name: String, local: IOLocal[Int]): IO[Unit] =
 *    local.update(_ + 1) >> local.get.flatMap(current => IO.println(s"fiber $$name: $$current"))
 *
 *  for {
 *    local   <- IOLocal(42)
 *    _       <- inc(1, local)
 *    _       <- inc(2, local)
 *    current <- local.get
 *    _       <- IO.println(s"fiber A: $$current")
 *  } yield ()
 *
 *  // output:
 *  // update 1: 43
 *  // update 2: 44
 *  // fiber A: 44
 * }}}
 *
 * ===A forked fiber operates on a copy of the parent [[IOLocal]]===
 *
 * A '''forked''' fiber (i.e. via `Spawn[F].start`) operates on a '''copy''' of the parent
 * `IOLocal`. Hence, the children operations are not reflected on the parent context.
 *
 * {{{
 *                       ┌────────────┐               ┌────────────┐
 *                  fork │  Fiber B   │ update(_ - 1) │  Fiber B   │
 *                ┌─────►│ (local 42) │──────────────►│ (local 41) │
 *                │      └────────────┘               └────────────┘
 * ┌────────────┐─┘                                   ┌────────────┐
 * │  Fiber A   │                                     │  Fiber A   │
 * │ (local 42) │────────────────────────────────────►│ (local 42) │
 * └────────────┘─┐                                   └────────────┘
 *                │      ┌────────────┐               ┌────────────┐
 *                │ fork │  Fiber C   │ update(_ + 1) │  Fiber C   │
 *                └─────►│ (local 42) │──────────────►│ (local 43) │
 *                       └────────────┘               └────────────┘
 * }}}
 *
 * {{{
 *  def update(name: String, local: IOLocal[Int], f: Int => Int): IO[Unit] =
 *    local.update(f) >> local.get.flatMap(current => IO.println(s"$$name: $$current"))
 *
 *  for {
 *    local   <- IOLocal(42)
 *    fiber1  <- update("fiber B", local, _ - 1).start
 *    fiber2  <- update("fiber C", local, _ + 1).start
 *    _       <- fiber1.joinWithNever
 *    _       <- fiber2.joinWithNever
 *    current <- local.get
 *    _       <- IO.println(s"fiber A: $$current")
 *  } yield ()
 *
 *  // output:
 *  // fiber B: 41
 *  // fiber C: 43
 *  // fiber A: 42
 * }}}
 *
 * ===Parent operations on [[IOLocal]] are invisible to children===
 *
 * {{{
 *                       ┌────────────┐               ┌────────────┐
 *                  fork │  Fiber B   │ update(_ + 1) │  Fiber B   │
 *                ┌─────►│ (local 42) │──────────────►│ (local 43) │
 *                │      └────────────┘               └────────────┘
 * ┌────────────┐─┘                                   ┌────────────┐
 * │  Fiber A   │        update(_ - 1)                │  Fiber A   │
 * │ (local 42) │────────────────────────────────────►│ (local 41) │
 * └────────────┘─┐                                   └────────────┘
 *                │      ┌────────────┐               ┌────────────┐
 *                │ fork │  Fiber C   │ update(_ + 2) │  Fiber C   │
 *                └─────►│ (local 42) │──────────────►│ (local 44) │
 *                       └────────────┘               └────────────┘
 * }}}
 *
 * {{{
 *  def update(name: String, local: IOLocal[Int], f: Int => Int): IO[Unit] =
 *    IO.sleep(1.second) >> local.update(f) >> local.get.flatMap(current => IO.println(s"$$name: $$current"))
 *
 *  for {
 *    local  <- IOLocal(42)
 *    fiber1 <- update("fiber B", local, _ + 1).start
 *    fiber2 <- update("fiber C", local, _ + 2).start
 *    _      <- fiber1.joinWithNever
 *    _      <- fiber2.joinWithNever
 *    _      <- update("fiber A", local, _ - 1)
 *  } yield ()
 *
 *  // output:
 *  // fiber B: 43
 *  // fiber C: 44
 *  // fiber A: 41
 * }}}
 *
 * @tparam A
 *   the type of the local value
 */
sealed trait IOLocal[A] {

  /**
   * Returns the current value.
   */
  def get: IO[A]

  /**
   * Sets the current value to `value`.
   */
  def set(value: A): IO[Unit]

  /**
   * Replaces the current value with the initial value.
   */
  def reset: IO[Unit]

  /**
   * Modifies the current value using the given update function.
   */
  def update(f: A => A): IO[Unit]

  /**
   * Like [[update]] but allows the update function to return an output value of type `B`.
   *
   * @see
   *   [[update]]
   */
  def modify[B](f: A => (A, B)): IO[B]

  /**
   * Replaces the current value with `value`, returning the previous value.
   *
   * The combination of [[get]] and [[set]].
   *
   * @see
   *   [[get]]
   * @see
   *   [[set]]
   */
  def getAndSet(value: A): IO[A]

  /**
   * Replaces the current value with the initial value, returning the previous value.
   *
   * The combination of [[get]] and [[reset]].
   *
   * @see
   *   [[get]]
   * @see
   *   [[reset]]
   */
  def getAndReset: IO[A]

  /**
   * Creates a scope with the given value. The original value is restored upon the finalization
   * of a resource. It means all changes made inside of the resource will not be propagated to
   * the outside.
   *
   * @example
   *
   * {{{
   *   for {
   *     local <- IOLocal(42)
   *     _     <- local.get // returns 42
   *     _     <- local.scope(0).surround(local.getAndSet(1)) // returns 0
   *     _     <- local.get // returns 42, even though 1 was set inside of the resource
   *   } yield ()
   * }}}
   *
   * @param value
   *   the value to make a scope with
   */
  def scope(value: A): Resource[IO, Unit]

}

object IOLocal {

  /**
   * Creates a new instance of [[IOLocal]] with the given default value.
   *
   * The creation is effectful, because [[IOLocal]] models mutable state, and allocating mutable
   * state is not pure.
   *
   * @param default
   *   the default value
   * @tparam A
   *   the type of the local value
   */
  def apply[A](default: A): IO[IOLocal[A]] =
    IO {
      new IOLocal[A] { self =>
        private[this] def getOrDefault(state: IOLocalState): A =
          state.getOrElse(self, default).asInstanceOf[A]

        override def get: IO[A] =
          IO.Local(state => (state, getOrDefault(state)))

        override def set(value: A): IO[Unit] =
          IO.Local(state => (state.updated(self, value), ()))

        override def reset: IO[Unit] =
          IO.Local(state => (state - self, ()))

        override def update(f: A => A): IO[Unit] =
          IO.Local(state => (state.updated(self, f(getOrDefault(state))), ()))

        override def modify[B](f: A => (A, B)): IO[B] =
          IO.Local { state =>
            val (a2, b) = f(getOrDefault(state))
            (state.updated(self, a2), b)
          }

        override def getAndSet(value: A): IO[A] =
          IO.Local(state => (state.updated(self, value), getOrDefault(state)))

        override def getAndReset: IO[A] =
          IO.Local(state => (state - self, getOrDefault(state)))

        override def scope(value: A): Resource[IO, Unit] =
          Resource.make(getAndSet(value))(p => set(p)).void
      }
    }

}
