package com.treode.store.local

import java.util.concurrent.{CountDownLatch, Executors}

import scala.language.postfixOps
import scala.util.Random

import com.treode.cluster.concurrent.{Callback, Scheduler}
import com.treode.pickle.Picklers
import com.treode.store.{Bytes, ReadBatch}
import com.treode.store.{Accessor, Transaction, TxClock, TxId, Value, WriteBatch}
import com.treode.store.TableId.apply

import org.scalatest.WordSpec

class LocalStoreSpec extends WordSpec {

  val Xid = TxId (Bytes ("Tx"))

  object Accounts extends Accessor (1, Picklers.fixedInt, Picklers.fixedInt)

  "The LocalStore" should {

    "serialize concurrent operations" in {

      val bits = 2
      val threads = 8
      val transfers = 1000
      val audits = 1000
      val opening = 1000

      val size = 1 << bits
      val supply = size * opening
      val store = new TestableTempStore
      val create =
        for (i <- 0 until size) yield Accounts.create (i, opening)
      store.writeExpectApply (0, create: _*) (tx => tx.commit (tx.ft+1))

      val executor = Executors.newScheduledThreadPool (threads)
      val scheduler = Scheduler (executor)

      val latch = new CountDownLatch (threads)

      // Check that the sum of the account balances equals the supply
      def audit (cb: Callback [Unit]) {
        val batch = ReadBatch (
            TxClock.now,
            for (i <- 0 until size) yield Accounts.read (i))
        store.read (batch, new StubReadCallback {
          override def apply (vs: Seq [Value]): Unit = scheduler.execute {
            val total = vs .map (Accounts.value (_) .get) .sum
            expectResult (supply) (total)
            cb()
          }})
      }

      // Transfer a random amount between two random accounts.
      def transfer (num: Int, cb: Callback [Unit]) {
        val x = Random.nextInt (size)
        var y = Random.nextInt (size)
        while (x == y)
          y = Random.nextInt (size)
        val rbatch = ReadBatch (TxClock.now, Accounts.read (x), Accounts.read (y))
        store.read (rbatch, new StubReadCallback {
          override def apply (vs: Seq [Value]): Unit = scheduler.execute {
            val ct = vs map (_.time) max
            val Seq (b1, b2) = vs map (Accounts.value (_) .get)
            val n = Random.nextInt (b1)
            val wbatch = WriteBatch (Xid, ct, ct,
                Accounts.update (x, b1-n), Accounts.update (y, b2+n))
            store.write (wbatch, new StubWriteCallback {
              override def apply (tx: Transaction): Unit = scheduler.execute {
                tx.commit (tx.ft+1)
                cb()
              }
              override def advance() = cb()
            })
          }})
      }

      // Conduct many transfers.
      def broker (num: Int) {
        var i = 0
        val loop = new Callback [Unit] {
          def apply (v: Unit): Unit = {
            if (i < transfers) {
              i += 1
              transfer (num, this)
            } else {
              latch.countDown()
            }}
          def fail (t: Throwable) = throw t
        }
        transfer (num, loop)
      }

      // Conduct many audits.
      def auditor() {
        val loop = new Callback [Unit] {
          def apply (v: Unit) {
            if (latch.getCount > 0)
              audit (this)
          }
          def fail (t: Throwable) = throw t
        }
        audit (loop)
      }

      for (i <- 0 until threads)
        broker (i)
      auditor()

      latch.await()
      executor.shutdown()
    }}}