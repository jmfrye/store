package com.treode.async

import scala.collection.generic.FilterMonadic

import Async.async

/** Run asynchronous operations simultaneously across a collection, await the completion of all
  * those operations, perhaps collecting the results.
  *
  * {{{
  * import com.treode.async.implicits._
  * def process (xs: Seq [Int]): Async [Unit] =
  *   for (x <- xs.latch.unit) {
  *     // Some asynchronous operation using x
  *   }
  * }}}
  */
class IterableLatch [A, R] private [async] (iter: FilterMonadic [A, R], size: Int) {

  private def run [A, R, B] (iter: FilterMonadic [A, R], cb: Callback [B]) (f: A => Async [B]): Unit =
    iter foreach (x => f (x) run (cb))

  /** The iterated method must yield a pair (key, value); the final result is a map. */
  object map {

    def foreach [K, V] (f: A => Async [(K, V)]): Async [Map [K, V]] =
      async { cb =>
        run [A, R, (K, V)] (iter, new MapLatch [K, V] (size, cb)) (f)
      }

    def filter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .map

    def withFilter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .map
  }

  /** The iterated method must yield a result; the final result is a sequence whose order matches
    * that of the input iterator.
    */
  object indexed {

    def foreach [C, B] (f: A => Async [B]) (implicit m: Manifest [B], w: A <:< (C, Int)): Async [Seq [B]] =
      async { cb =>
        run [A, R, (Int, B)] (iter, new IndexedLatch (size, cb)) {
          x => f (x) map ((x._2, _))
        }}

    def filter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .indexed

    def withFilter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .indexed
  }

  /** The iterated method must yield a result; the final result is a sequence whose order depends
    * on the scheduling of the asynchronous operations.
    */
  object seq {

    def foreach [B] (f: A => Async [B]) (implicit m: Manifest [B]): Async [Seq [B]] =
      async { cb =>
        run [A, R, B] (iter, new SeqLatch [B] (size, cb)) (f)
      }

    def filter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .seq

    def withFilter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .seq
  }

  /** The result of the iterated method is ignored; the final result is a Unit. */
  object unit {

    def foreach [B] (f: A => Async [B]): Async [Unit] =
      async { cb =>
        run [A, R, B] (iter, new CountingLatch [B] (size, cb)) (f)
      }

    def filter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .unit

    def withFilter (p: A => Boolean) =
      new IterableLatch (iter.withFilter (p), size) .unit
  }}
