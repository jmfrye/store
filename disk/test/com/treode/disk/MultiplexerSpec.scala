package com.treode.disk

import scala.collection.mutable.UnrolledBuffer

import com.treode.async.{AsyncTestTools, StubScheduler}
import org.scalatest.FlatSpec

import AsyncTestTools._
import DispatcherTestTools._

class MultiplexerSpec extends FlatSpec {

  "The Multiplexer" should "relay messages from the dispatcher" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val mplx = new Multiplexer (dsp)
    dsp.send (1)
    mplx.expect (1)
    mplx.replace (list())
    mplx.expectNone()
  }

  it should "return open rejects to the dispatcher" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val mplx = new Multiplexer (dsp)
    dsp.send (1)
    dsp.send (2)
    mplx.expect (1, 2)
    mplx.replace (list (2))
    dsp.expect (2)
    dsp.replace (list())
    mplx.expectNone()
  }

  it should "relay exclusive messages when available" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val mplx = new Multiplexer (dsp)
    mplx.send (2)
    mplx.expect (2)
    mplx.replace (list())
    mplx.expectNone()
  }

  it should "recycle exclusive messages rejected by an earlier receptor" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val rcpt1 = dsp.receptor()
    val mplx = new Multiplexer (dsp)
    val rcpt2 = mplx.receptor()
    scheduler.runTasks()
    mplx.send (2)
    rcpt2.expect (2)
    mplx.replace (list (2))
    mplx.expect (2)
    mplx.replace (list())
    rcpt1.expectNone()
  }

  it should "recycle exclusive messages rejected by a later receptor" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val rcpt1 = dsp.receptor()
    val mplx = new Multiplexer (dsp)
    mplx.send (2)
    mplx.expect (2)
    mplx.replace (list (2))
    mplx.expect (2)
    mplx.replace (list())
    rcpt1.expectNone()
  }

  it should "close immediately when a receiver is waiting" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val mplx = new Multiplexer (dsp)
    val rcpt = mplx.receptor()
    mplx.close() .pass
    dsp.send (1)
    rcpt.expectNone()
    dsp.expect (1)
  }

  it should "delay close when a until a receiver is waiting" in {
    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)
    val mplx = new Multiplexer (dsp)
    val cb = mplx.close().capture()
    scheduler.runTasks()
    cb.assertNotInvoked()
    dsp.send (1)
    scheduler.runTasks()
    cb.assertNotInvoked()
    val rcpt = mplx.receptor()
    scheduler.runTasks()
    cb.passed
    rcpt.expectNone()
    dsp.expect (1)
  }

  it should "recycle rejects until accepted" in {

    implicit val scheduler = StubScheduler.random()
    val dsp = new Dispatcher [Int] (scheduler)

    var received = Set.empty [Int]

    /* Bits of 0xF indicate open (0) or exclusive (non-zero).
     * Accept upto 5 messages m where (m & 0xF0 == i)
     * Ensure m was not already accepted and (m & 0xF == 0 || m & 0xF == i).
     */
    def receive (mplx: Multiplexer [Int], i: Int) (msgs: UnrolledBuffer [Int]) {
      assert (!(msgs exists (received contains _)))
      assert (msgs forall (m => ((m & 0xF) == 0 || (m & 0xF) == i)))
      val (accepts, rejects) = msgs.partition (m => (m & 0xF0) == (i << 4))
      mplx.replace ((accepts drop 5) ++ rejects)
      received ++= accepts take 5
      mplx.receive (receive (mplx, i))
    }

    var expected = Set.empty [Int]
    val count = 100
    for (i <- 0 until count) {
      dsp.send ((i << 8) | 0x10)
      expected += (i << 8) | 0x10
      dsp.send ((i << 8) | 0x20)
      expected += (i << 8) | 0x20
      dsp.send ((i << 8) | 0x30)
      expected += (i << 8) | 0x30
    }
    for (i <- 1 to 3) {
      val mplx = new Multiplexer (dsp)
      dsp.receive (receive (mplx, i))
      for (j <- 0 until count) {
        mplx.send ((j << 8) | (i << 4) | i)
        expected += (j << 8) | (i << 4) | i
      }}

    scheduler.runTasks()

    assertResult (expected) (received)
  }}
