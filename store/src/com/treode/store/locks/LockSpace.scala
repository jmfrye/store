package com.treode.store.locks

import scala.collection.SortedSet
import scala.language.postfixOps

import com.treode.async.Scheduler
import com.treode.store.{StoreConfig, TxClock}

private [store] class LockSpace (implicit config: StoreConfig) {
  import config.lockSpaceBits

  private val mask = (1 << lockSpaceBits) - 1
  private val locks = Array.fill (1 << lockSpaceBits) (new Lock)

  // Returns true if the lock was granted immediately.  Queues the acquisition to be called back
  // later otherwise.
  private [locks] def read (id: Int, r: LockReader): Boolean =
    locks (id) .read (r)

  // Returns the forecasted timestamp, and the writer must commit at a greater timestamp, if the
  // lock was granted immediately.  Queues the acquisition to be called back otherwise.
  private [locks] def write (id: Int, w: LockWriter): Option [TxClock] =
    locks (id) .write (w)

  private [locks] def release (id: Int, w: LockWriter): Unit =
    locks (id) .release (w)

  def read (rt: TxClock, ids: Seq [Int]) (cb: => Any): Unit =
    new LockReader (rt, Scheduler.toRunnable (cb)) .init (this, ids map (_ & mask) toSet)

  def write (ft: TxClock, ids: Seq [Int]) (cb: LockSet => Any): Unit =
    new LockWriter (this, ft, SortedSet (ids map (_ & mask): _*), cb) .init()
}
