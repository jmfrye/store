package com.treode

import java.lang.{Integer => JavaInt, Long => JavaLong}
import java.nio.channels.CompletionHandler
import scala.util.{Failure, Random, Success, Try}

import com.treode.async.implicits._

package async {

  private class CallbackException (thrown: Throwable) extends Exception (thrown)

  private class ReturnException extends Exception {
    override def getMessage = "The return keyword is not allowed in an async definition."
  }}

/** The async package defines the [[com.treode.async.Async Async]] class which an asynchronous
  * method may return as its result, and it is a good place to begin reading.
  *
  * This package also defines asynchronous [[com.treode.async.io.File files]] and
  * [[com.treode.async.io.Socket sockets]], and it provides [[com.treode.async.Fiber fibers]] that
  * are not quite entirely unlike actors.
  *
  * See the [[com.treode.async.stubs stubs]] package for an overview of testing asynchronous
  * methods.
  */
package object async {

  import java.nio.file.Paths
  import java.util.concurrent.Executors
  import io.File
  import com.treode.buffer.PagedBuffer
  val executor = Executors.newScheduledThreadPool (8)
  val scheduler = Scheduler (executor)
  val file = File.open (Paths.get ("file"), executor)
  val input = PagedBuffer (12)
  file.fill (input, 0, 1024) run {
    case Success (_) => // do something with input
    case Failure (t) => // do something with exception
  }

  type Callback [A] = Try [A] => Any

  object Callback {

    /** Adapts Callback to Java's NIO CompletionHandler. */
    private [async] object IntHandler extends CompletionHandler [JavaInt, Callback [Int]] {
      def completed (v: JavaInt, cb: Callback [Int]) = cb.pass (v)
      def failed (t: Throwable, cb: Callback [Int]) = cb.fail (t)
    }

    /** Adapts Callback to Java's NIO CompletionHandler. */
    private [async] object LongHandler extends CompletionHandler [JavaLong, Callback [Long]] {
      def completed (v: JavaLong, cb: Callback [Long]) = cb.pass (v)
      def failed (t: Throwable, cb: Callback [Long]) = cb.fail (t)
    }

    /** Adapts Callback to Java's NIO CompletionHandler. */
    private [async] object UnitHandler extends CompletionHandler [Void, Callback [Unit]] {
      def completed (v: Void, cb: Callback [Unit]) = cb.pass()
      def failed (t: Throwable, cb: Callback [Unit]) = cb.fail (t)
    }

    def fix [A] (f: Callback [A] => Try [A] => Any): Callback [A] =
      new Callback [A] {
        def apply (v: Try [A]) = f (this) (v)
      }

    def fanout [A] (cbs: Traversable [Callback [A]], scheduler: Scheduler): Callback [A] =
      (v => cbs foreach (scheduler.execute (_, v)))

    def ignore [A]: Callback [A] = {
        case Success (v) => ()
        case Failure (t) => throw t
      }}}
