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

import org.specs2.ScalaCheck

trait IOPlatformSpecification { self: BaseSpec with ScalaCheck =>

  def platformSpecs = "platform" should {
    "realTimeInstant should return an Instant constructed from realTime" in ticked {
      implicit ticker =>
        val op = for {
          now <- IO.realTimeInstant
          realTime <- IO.realTime
        } yield now.toEpochMilli == realTime.toMillis

        op must completeAs(true)
    }
  }
}
