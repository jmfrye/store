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

package com.treode.disk.edit

import com.treode.async.Callback
import com.treode.buffer.Output
import com.treode.disk.{RecordDescriptor,TypeId}

private abstract class PickledRecord (
    val tid: TypeId,
    val cb: Callback [Unit]
) {
  def byteSize: Int
  def write (out: Output)
}

private object PickledRecord {

  def apply [R] (
      desc: RecordDescriptor [R],
      entry: R,
      cb: Callback [Unit]
  ): PickledRecord =
    new PickledRecord (desc.id, cb) {
      def byteSize = desc.prec.byteSize (entry)
      def write (out: Output) = desc.prec.pickle (entry, out)
      override def toString = s"PickledRecord($entry)"
    }}
