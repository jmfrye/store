package com.treode.store.disk2

import java.lang.{Long => JLong}
import java.util.Objects
import com.treode.pickle.Picklers

class DiskDriveConfig private (
    val segmentBits: Int,
    val blockBits: Int,
    val diskBytes: Long) {

  val segmentBytes = 1 << segmentBits
  val segmentMask = segmentBytes - 1
  val segmentCount = ((diskBytes + segmentMask.toLong) >> segmentBits).toInt

  private [disk2] def segment (num: Int): Segment = {
    require (0 <= num && num < segmentCount)
    val pos = if (num == 0) DiskLeadBytes else num << segmentBits
    val end = (num + 1) << segmentBits
    val limit = if (end > diskBytes) diskBytes else end
    Segment (num, pos, limit)
  }

  override def hashCode: Int =
    Objects.hash (segmentBits: JLong, blockBits: JLong, diskBytes: JLong)

  override def equals (other: Any): Boolean =
    other match {
      case that: DiskDriveConfig =>
        segmentBits == that.segmentBits &&
        blockBits == that.blockBits &&
        diskBytes == that.diskBytes
      case _ =>
        false
    }

  override def toString = s"DiskDriveConfig($segmentBits, $blockBits, $diskBytes)"
}

object DiskDriveConfig {

  def apply (
      segmentBits: Int,
      blockBits: Int,
      diskBytes: Long): DiskDriveConfig = {

    require (segmentBits > 0, "segmentBits must be greater than 0")
    require (blockBits > 0, "blockBits must be greater than 0")
    require (diskBytes > 0, "diskBytes must be greater than 0")

    new DiskDriveConfig (
        segmentBits,
        blockBits,
        diskBytes)
  }

  val pickle = {
    import Picklers._
    wrap (int, int, long)
    .build ((apply _).tupled)
    .inspect (v => (v.segmentBits, v.blockBits, v.diskBytes))
  }}