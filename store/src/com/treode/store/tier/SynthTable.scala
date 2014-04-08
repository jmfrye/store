package com.treode.store.tier

import java.util.concurrent.locks.ReentrantReadWriteLock

import com.treode.async.{Async, AsyncIterator, Callback, Scheduler}
import com.treode.disk.{Disks, ObjectId, PageHandler, PageDescriptor, Position}
import com.treode.store.{Bytes, Cell, CellIterator, StoreConfig, TxClock}

import Async.{async, supply}
import TierTable.Meta

private class SynthTable [K, V] (

    val desc: TierDescriptor [K, V],

    val obj: ObjectId,

    // To lock the generation and references to the primary and secondary; this locks the references
    // only, while the skip list manages concurrent readers and writers of entries.  Writing to the
    // table requires only a read lock on the references to ensure that the compactor does not change
    // them. The compactor uses a write lock to move the primary to secondary, allocate a new
    // primary, and increment the generation.
    lock: ReentrantReadWriteLock,

    var gen: Long,

    // This resides in memory and it is the only tier that is written.
    var primary: MemTier,

    // This tier resides in memory and is being compacted and written to disk.
    var secondary: MemTier,

    // The position of each tier on disk.
    var tiers: Tiers

) (implicit
    scheduler: Scheduler,
    disks: Disks,
    config: StoreConfig
) extends TierTable {
  import scheduler.whilst

  private val readLock = lock.readLock()
  private val writeLock = lock.writeLock()

  def ceiling (key: Bytes, time: TxClock): Async [Cell] = {

    val mkey = MemKey (key, time)

    readLock.lock()
    val (primary, secondary, tiers) = try {
      (this.primary, this.secondary, this.tiers)
    } finally {
      readLock.unlock()
    }

    var candidate = Cell.sentinel
    var entry = primary.ceilingEntry (mkey)
    if (entry != null) {
      val MemKey (k, t) = entry.getKey
      if (key == k && candidate.time < t) {
        candidate = memTierEntryToCell (entry)
      }}

    entry = secondary.ceilingEntry (mkey)
    if (entry != null) {
      val MemKey (k, t) = entry.getKey
      if (key == k && candidate.time < t) {
        candidate = memTierEntryToCell (entry)
      }}

    var i = 0
    for {
      _ <-
        whilst (i < tiers.size) {
          for (cell <- tiers (i) .ceiling (desc, key, time)) yield {
            cell match {
              case Some (c @ Cell (k, t, v)) if key == k && candidate.time < t =>
                candidate = c
              case _ =>
                ()
            }
            i += 1
          }}
    } yield {
      if (candidate == Cell.sentinel)
        Cell (key, 0, None)
      else
        candidate
    }}

  def get (key: Bytes): Async [Option [Bytes]] =
    ceiling (key, TxClock.max) .map (_.value)

  def put (key: Bytes, time: TxClock, value: Bytes): Long = {
    readLock.lock()
    try {
      primary.put (MemKey (key, time), Some (value))
      gen
    } finally {
      readLock.unlock()
    }}

  def delete (key: Bytes, time: TxClock): Long = {
    readLock.lock()
    try {
      primary.put (MemKey (key, time), None)
      gen
    } finally {
      readLock.unlock()
    }}

  def iterator: CellIterator = {
    readLock.lock()
    val (primary, secondary, tiers) = try {
      (this.primary, this.secondary, this.tiers)
    } finally {
      readLock.unlock()
    }
    TierIterator.merge (desc, primary, secondary, tiers) .dedupe
  }

  def probe (groups: Set [Long]): Set [Long] = {
    readLock.lock()
    val (tiers) = try {
      this.tiers
    } finally {
      readLock.unlock()
    }
    tiers.active
  }

  def compact (groups: Set [Long]): Async [Meta] =
    checkpoint()

  def checkpoint(): Async [Meta] = disks.join {

    writeLock.lock()
    val (gen, primary, tiers) = try {
      require (secondary.isEmpty)
      val g = this.gen
      val p = this.primary
      this.gen += 1
      this.primary = secondary
      this.secondary = p
      (g, p, this.tiers)
    } finally {
      writeLock.unlock()
    }

    val iter = TierIterator.merge (desc, primary, emptyMemTier, tiers) .dedupe
    for {
      tier <- TierBuilder.build (desc, obj, gen, iter)
    } yield {
      val tiers = Tiers (tier)
      val meta = new Meta (gen, tiers)
      writeLock.lock()
      try {
        this.secondary = newMemTier
        this.tiers = tiers
      } finally {
        writeLock.unlock()
      }
      meta
    }}}

private object SynthTable {

  def apply [K, V] (desc: TierDescriptor [K,V], obj: ObjectId) (
      implicit scheduler: Scheduler, disk: Disks, config: StoreConfig): SynthTable [K, V] = {
    val lock = new ReentrantReadWriteLock
    new SynthTable (desc, obj, lock, 0, new MemTier, new MemTier, Tiers.empty)
  }}
