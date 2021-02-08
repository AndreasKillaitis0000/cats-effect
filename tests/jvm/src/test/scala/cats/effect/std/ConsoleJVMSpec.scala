/*
 * Copyright 2020-2021 Typelevel
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
package std

import cats.syntax.all._
import org.specs2.matcher.MatchResult

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.{Charset, StandardCharsets}

import scala.io.Source

class ConsoleJVMSpec extends BaseSpec {
  sequential

  private def fileLines(name: String, charset: Charset): IO[List[String]] = {
    val acquire =
      IO.blocking(Source.fromResource(s"readline-test.$name.txt")(charset))
    val release = (src: Source) => IO(src.close())
    Resource
      .make(acquire)(release)
      .use(src => IO.interruptible(false)(src.getLines().toList))
      .handleErrorWith(_ => IO.pure(Nil))
  }

  private def consoleReadLines(lines: List[String], charset: Charset): IO[List[String]] = {
    def inputStream: Resource[IO, InputStream] = {
      val acquire =
        IO {
          val bytes = lines.mkString(System.lineSeparator()).getBytes(charset)
          new ByteArrayInputStream(bytes)
        }
      val release = (in: InputStream) => IO(in.close())
      Resource.make(acquire)(release)
    }

    def replaceStandardIn(in: InputStream): Resource[IO, Unit] = {
      def replace(in: InputStream): IO[InputStream] =
        for {
          std <- IO(System.in)
          _ <- IO(System.setIn(in))
        } yield std

      def restore(in: InputStream): IO[Unit] =
        IO(System.setIn(in))

      Resource.make(replace(in))(restore).void
    }

    val test = for {
      in <- inputStream
      _ <- replaceStandardIn(in)
    } yield ()

    def loop(acc: List[String]): IO[List[String]] =
      Console[IO]
        .readLineWithCharset(charset)
        .flatMap(ln => loop(ln :: acc))
        .handleErrorWith(_ => IO.pure(acc.reverse))

    test.use(_ => loop(Nil))
  }

  private def readLineTest(name: String, charset: Charset): IO[MatchResult[List[String]]] =
    for {
      rawLines <- fileLines(name, charset)
      lines <- consoleReadLines(rawLines, charset)
      result <- IO(lines must beEqualTo(rawLines))
    } yield result

  "Console" should {
    "read all lines from an ISO-8859-1 encoded file" in real {
      val cs = StandardCharsets.ISO_8859_1
      readLineTest(cs.name(), cs)
    }

    "read all lines from a US-ASCII encoded file" in real {
      val cs = StandardCharsets.US_ASCII
      readLineTest(cs.name(), cs)
    }

    "read all lines from a UTF-8 encoded file" in real {
      val cs = StandardCharsets.UTF_8
      readLineTest(cs.name(), cs)
    }

    "read all lines from a UTF-16 encoded file" in real {
      val cs = StandardCharsets.UTF_16
      readLineTest(cs.name(), cs)
    }

    "read all lines from a UTF-16BE encoded file" in real {
      val cs = StandardCharsets.UTF_16BE
      readLineTest(cs.name(), cs)
    }

    "read all lines from a UTF-16LE encoded file" in real {
      val cs = StandardCharsets.UTF_16LE
      readLineTest(cs.name(), cs)
    }
  }
}
