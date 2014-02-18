package com.treode.disk

import com.treode.async.Async
import com.treode.async.io.File
import com.treode.buffer.PagedBuffer

import Async.guard

private case class SuperBlock (
    id: Int,
    boot: BootBlock,
    geometry: DiskGeometry,
    draining: Boolean,
    free: IntSet,
    logSeg: Int,
    logHead: Long,
    pageSeg: Int,
    pagePos: Long)

private object SuperBlock {

  val pickler = {
    import DiskPicklers._
    wrap (uint, boot, geometry, boolean, intSet, uint, ulong, uint, ulong)
    .build ((SuperBlock.apply _).tupled)
    .inspect (v => (
        v.id, v.boot, v.geometry, v.draining, v.free, v.logSeg, v.logHead, v.pageSeg, v.pagePos))
  }

  def position (gen: Int) (implicit config: DisksConfig): Long =
    if ((gen & 0x1) == 0) 0L else config.superBlockBytes

  def write (gen: Int, superb: SuperBlock, file: File) (implicit config: DisksConfig): Async [Unit] =
    guard {
      val buf = PagedBuffer (12)
      pickler.pickle (superb, buf)
      file.flush (buf, position (gen))
    }}
