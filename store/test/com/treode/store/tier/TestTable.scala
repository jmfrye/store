package com.treode.store.tier

import com.treode.async.{Async, AsyncIterator, Callback, Scheduler}
import com.treode.disk.{Disks, RecordDescriptor, Recovery, RootDescriptor}
import com.treode.store.{Bytes, StoreConfig, StorePicklers}

import Async.{async, supply}

private trait TestTable {

  def get (key: Int): Async [Option [Int]]

  def iterator: AsyncIterator [TestCell]

  def put (key: Int, value: Int): Async [Unit]

  def delete (key: Int): Async [Unit]
}

private object TestTable {

  val descriptor = {
    import StorePicklers._
    TierDescriptor (0x0B918C28, string, int)
  }

  val root = {
    import StorePicklers._
    RootDescriptor (0x2B30D8AF, tierMeta)
  }

  val put = {
    import StorePicklers._
    RecordDescriptor (0x6AC99D09, tuple (ulong, int, int))
  }

  val delete = {
    import StorePicklers._
    RecordDescriptor (0x4D620837, tuple (ulong, int))
  }

  val checkpoint = {
    import StorePicklers._
    RecordDescriptor (0xA67C3DD1, tierMeta)
  }

  def recover (cb: Callback [TestTable]) (
      implicit scheduler: Scheduler, recovery: Recovery, config: StoreConfig) {

    val medic = TierMedic (descriptor)

    root.reload { tiers => implicit reload =>
      medic.checkpoint (tiers)
      supply(())
    }

    put.replay { case (gen, key, value) =>
      medic.put (gen, Bytes (key), Bytes (value))
    }

    delete.replay { case (gen, key) =>
      medic.delete (gen, Bytes (key))
    }

    recovery.launch { implicit launch =>
      import launch.disks
      val table = medic.close()
      root.checkpoint (table.checkpoint())
      //pager.handle (table)
      cb.pass (new LoggedTable (table))
      supply (())
    }}}