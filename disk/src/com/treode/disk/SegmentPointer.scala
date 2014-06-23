package com.treode.disk

import com.treode.async.Async

private class SegmentPointer private (
    val disk: DiskDrive,
    val bounds: SegmentBounds
) {

  def num = bounds.num
  def base = bounds.base
  def limit = bounds.limit

  def compacting(): Unit =
    disk.compacting (Seq (this))

  def probe(): Async [PageLedger] =
    PageLedger.read (disk.file, disk.geom, bounds.base)

  def free(): Unit =
    disk.free (this)

  override def equals (other: Any): Boolean =
    other match {
      case that: SegmentPointer =>
        (disk.id, bounds.num) == (that.disk.id, that.bounds.num)
      case _ => false
    }

  override def hashCode: Int =
    (disk.id, bounds.num).hashCode

  override def toString: String =
    s"SegmentPointer(${disk.id}, ${bounds.num})"
}

private object SegmentPointer {

  def apply (disk: DiskDrive, bounds: SegmentBounds): SegmentPointer =
    new SegmentPointer (disk, bounds)
}
