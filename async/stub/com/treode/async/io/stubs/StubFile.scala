package com.treode.async.io.stubs

import java.io.EOFException
import java.util.{Arrays, ArrayDeque}
import scala.util.{Failure, Success}

import com.treode.async.{Async, Callback}
import com.treode.async.implicits._
import com.treode.async.stubs.{CallbackCaptor, StubScheduler}
import com.treode.async.io.File
import com.treode.buffer.PagedBuffer

import Async.async

/** A stub file that keeps its data in a byte array, eliminating the risk that tests fail to clean
  * up test files and leave turds all over your disk.  The stub file will grow as necessary to
  * accommodate `flush`; use the `size` parameter to initialize the underlying byte array to
  * avoid repeatedly growing it and save time on copies.
  *
  * In a multithread context, the `flush` and `fill` methods will do something much like a real
  * file would do: they will depend on the vagaries of the underling scheduler.  The mechanism
  * for capturing calls to `flush` and `fill` should be used only with the single-threaded
  * [[com.treode.async.stubs.StubScheduler StubScheduler]].
  */
class StubFile (size: Int = 0) (implicit _scheduler: StubScheduler) extends File (null) {

  private var data = new Array [Byte] (size)
  private var stack = new ArrayDeque [Callback [Unit]]

  var scheduler: StubScheduler = _scheduler

  /** If true, the next call to `flush` or `fill` will be captured and push on a stack. */
  var stop: Boolean = false

  var closed = false

  /** If true, a call to `flush` or `fill` was captured. */
  def hasLast: Boolean = !stack.isEmpty

  /** Pop the most recent call to `flush` or `fill` and return a callback which you can
    * `pass` or `fail`.
    */
  def last: Callback [Unit] = stack.pop()

  private def _stop (f: Callback [Unit] => Any): Async [Unit] = {
    require (!closed, "File has been closed")
    async { cb =>
      if (stop) {
        stack.push {
          case Success (v) =>
            f (cb)
          case Failure (t) =>
            cb.fail (t)
        }
      } else {
        f (cb)
      }}}

  override def fill (input: PagedBuffer, pos: Long, len: Int): Async [Unit] =
    _stop { cb =>
      try {
        require (pos >= 0)
        require (pos + len < Int.MaxValue)
        if (len <= input.readableBytes) {
          scheduler.pass (cb, ())
        } else if (data.length < pos) {
          scheduler.fail (cb, new EOFException)
        } else  {
          input.capacity (input.readPos + len)
          val p = pos.toInt + input.readableBytes
          val n = math.min (data.length - p, input.writeableBytes)
          input.writeBytes (data, pos.toInt + input.readableBytes, n)
          if (data.length < pos + len) {
            scheduler.fail (cb, new EOFException)
          } else {
            scheduler.pass (cb, ())
          }}
      } catch {
        case t: Throwable => scheduler.fail (cb, t)
      }}

  override def flush (output: PagedBuffer, pos: Long): Async [Unit] =
    _stop { cb =>
      try {
        require (pos >= 0)
        require (pos + output.readableBytes < Int.MaxValue)
        if (data.length < pos + output.readableBytes)
          data = Arrays.copyOf (data, pos.toInt + output.readableBytes)
        output.readBytes (data, pos.toInt, output.readableBytes)
        scheduler.pass (cb, ())
      } catch {
        case t: Throwable => scheduler.fail (cb, t)
      }}

  override def close(): Unit =
    closed = true

  override def toString = s"StubFile(size=${data.length})"
}
