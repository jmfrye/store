package com.treode.store.paxos

import scala.util.Random

import com.treode.async.{Async, Scheduler}
import com.treode.cluster.{Cluster, ReplyTracker}
import com.treode.disk.Disks
import com.treode.store.{Atlas, Bytes, Paxos, StoreConfig, TxClock}
import com.treode.store.tier.TierTable

import Async.async
import PaxosKit.locator

private class PaxosKit (
    val archive: TierTable
) (implicit
    val random: Random,
    val scheduler: Scheduler,
    val cluster: Cluster,
    val disks: Disks,
    val atlas: Atlas,
    val config: StoreConfig
) extends Paxos {

  val acceptors = new Acceptors (this)

  val proposers = new Proposers (this)

  def locate (key: Bytes, time: TxClock): ReplyTracker =
    atlas.locate (locator, (key, time)) .track

  def lead (key: Bytes, time: TxClock, value: Bytes): Async [Bytes] =
    proposers.propose (0, key, time, value)

  def propose (key: Bytes, time: TxClock, value: Bytes): Async [Bytes] =
    proposers.propose (random.nextInt (17) + 1, key, time, value)
}

private [store] object PaxosKit {

  val locator = {
    import PaxosPicklers._
    tuple (bytes, txClock)
  }

  def recover () (implicit
    random: Random,
    scheduler: Scheduler,
    cluster: Cluster,
    recovery: Disks.Recovery,
    config: StoreConfig): Paxos.Recovery =
  new RecoveryKit
}
