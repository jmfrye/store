package com.treode.disk

import com.treode.async.Async
import com.treode.async.io.File
import com.treode.buffer.PagedBuffer

import Async.guard

private case class SuperBlock (
    id: Int,
    boot: BootBlock,
    geometry: DriveGeometry,
    draining: Boolean,
    free: IntSet,
    logHead: Long)

private object SuperBlock {

  val pickler = {
    import DiskPicklers._
    // Tagged for forwards compatibility.
    tagged [SuperBlock] (
        0x0024811306495C5FL ->
            wrap (uint, boot, geometry, boolean, intSet, ulong)
            .build ((SuperBlock.apply _).tupled)
            .inspect (v => (
                v.id, v.boot, v.geometry, v.draining, v.free, v.logHead)))
  }

  def position (gen: Int) (implicit config: Disk.Config): Long =
    if ((gen & 0x1) == 0) 0L else config.superBlockBytes

  def clear (gen: Int, file: File) (implicit config: Disk.Config): Async [Unit] =
    guard {
      val buf = PagedBuffer (12)
      while (buf.writePos < config.superBlockBytes)
        buf.writeInt (0)
      file.flush (buf, position (gen))
    }

  def write (superb: SuperBlock, file: File) (implicit config: Disk.Config): Async [Unit] =
    guard {
      val buf = PagedBuffer (12)
      pickler.pickle (superb, buf)
      if (buf.writePos > config.superBlockBytes)
        throw new SuperblockOverflowException
      buf.writePos = config.superBlockBytes
      file.flush (buf, position (superb.boot.gen))
    }}
