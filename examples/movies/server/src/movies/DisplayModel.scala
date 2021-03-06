/*
 * Copyright 2014 Treode, Inc.
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

package movies

import com.fasterxml.jackson.annotation.JsonIgnore
import com.treode.store.alt.Transaction
import org.joda.time.DateTime

import movies.{PhysicalModel => PM}

/** See README.md. */
object DisplayModel {

  case class Movie (id: String, title: String, released: DateTime, cast: Seq [CastMember]) {

    @JsonIgnore
    lazy val actorIds =
      cast orDefault (Seq.empty) filter (_.actorId != null) map (_.actorId)

    @JsonIgnore
    lazy val titleLowerCase =
      if (title == null) null else title.toLowerCase
  }

  object Movie {

    def apply (tx: Transaction, movieId: String, movie: PM.Movie): Movie = {
      val cast = tx.get (PM.CastTable) (movieId) .getOrElse (PM.Cast.empty)
      new Movie (movieId, movie.title, movie.released, CastMember.convert (tx, cast))
    }}

  case class CastMember (actorId: String, actor: String, role: String)

  object CastMember {

    def apply (tx: Transaction, member: PM.CastMember): CastMember = {
      val actor = tx.get (PM.ActorTable) (member.actorId) .get
      new CastMember (member.actorId, actor.name, member.role)
    }

    def convert (tx: Transaction, cast: PM.Cast): Seq [CastMember] =
      for (member <- cast.members)
        yield CastMember (tx, member)
  }

  case class Actor (id: String, name: String, born: DateTime, roles: Seq [Role]) {

    @JsonIgnore
    lazy val movieIds =
      roles orDefault (Seq.empty) filter (_.movieId != null) map (_.movieId)

    @JsonIgnore
    lazy val nameLowerCase =
      if (name == null) null else name.toLowerCase
  }

  object Actor {

    def apply (tx: Transaction, actorId: String, actor: PM.Actor): Actor = {
      val roles = tx.get (PM.RolesTable) (actorId) .getOrElse (PM.Roles.empty)
      new Actor (actorId, actor.name, actor.born, Role.convert (tx, roles))
    }}

  case class Role (movieId: String, title: String, role: String)

  object Role {

    def apply (tx: Transaction, role: PM.Role): Role = {
      val movie = tx.get (PM.MovieTable) (role.movieId) .get
      new Role (role.movieId, movie.title, role.role)
    }

    def convert (tx: Transaction, roles: PM.Roles): Seq [Role] =
      for (role <- roles.roles)
        yield Role (tx, role)
  }}
