/*
 * Copyright 2013 http4s.org
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

package org.http4s
package headers

import cats.data.NonEmptyList
import org.typelevel.ci._

object Upgrade {

  def apply(head: CIString, tail: CIString*): Upgrade =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[Upgrade] =
    ParseResult.fromParser(parser, "Invalid Upgrade header")(s)

  private[http4s] val parser = ???

  implicit val headerInstance: Header[Upgrade, Header.Recurring] =
    Header.createRendered(
      ci"Upgrade",
      _.values,
      parse
    )
}

final case class Upgrade(values: NonEmptyList[CIString])