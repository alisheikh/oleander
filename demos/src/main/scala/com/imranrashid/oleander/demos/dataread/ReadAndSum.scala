package com.imranrashid.oleander.demos.dataread

import com.quantifind.sumac.{ArgMain, FieldArgs}
import java.io.{RandomAccessFile, File}
import com.imranrashid.oleander.BB2Itr
import java.nio.ByteBuffer
import java.nio.channels.FileChannel.MapMode

/**
 *
 */
object ReadAndSum extends ArgMain[ReadAndSumArgs] {
  def main(args: ReadAndSumArgs) {
    val (bb,itr) = time("load"){loadData(args)}
    ptime("regular")(regularSum(args, itr))
    ptime("regular")(regularSum(args, itr))
    ptime("bb kahan")(bbKahanSum(args, itr))
    ptime("pojo kahan")(pojoKahanSum(args, itr))
    ptime("bb kahan")(bbKahanSum(args, itr))
    fastRegularSum(args)
    fastBBKahanSum(args)
    fastPOJOKahanSum(args)

    ptime("range regular"){rangeRegularSum(args, bb)}
  }

  def loadData(args: ReadAndSumArgs) = {
    val (bb, _) = BB2Itr.mmap(args.inputFile, load = true)
    val itr = BB2Itr.byteBufferToIterable[DataPointIm](bb)
    (bb, itr)
  }

  def ptime[T](name: String)(f: => T) {
    val r = time(name)(f)
    println("result = " + r)
    println()
  }

  def time[T](name: String)(f: => T): T = {
    val start = System.nanoTime()
    val result = f
    val end = System.nanoTime()
    println(name + "\t" + (end - start).toDouble / 1e9)
    result
  }

  def regularSum(args: ReadAndSumArgs, data: Traversable[DataPointIm]): Float = {
    val bucketSums = new Array[Float](args.nBuckets)
    data.foreach{dp =>
      bucketSums(dp.bucket) += dp.value
    }
    bucketSums(0)
  }

  def rangeRegularSum(args: ReadAndSumArgs, data: ByteBuffer): Float = {
    val bucketSums = new Array[Float](args.nBuckets)
    val (dp, range) = BB2Itr.bbWithRange[DataPointIm](data)
    range.foreach{pos =>
      dp.setBuffer(data, pos)
      bucketSums(dp.bucket) += dp.value
    }
    bucketSums(0)
  }


  def bbKahanSum(args: ReadAndSumArgs, data: Traversable[DataPointIm]): Float =  {
    val bbsum = ByteBuffer.allocate(args.nBuckets * 8)
    val sums = BB2Itr.indexedBB2[BBKahanSummer](bbsum)
    data.foreach{dp =>
      sums(dp.bucket) += dp.value
    }
    sums(0).sum
  }

  def pojoKahanSum(args: ReadAndSumArgs, data: Traversable[DataPointIm]): Float = {
    val sums = new Array[POJOKahanSummer](args.nBuckets)
    (0 until args.nBuckets).foreach{idx => sums(idx) = new POJOKahanSummer}
    data.foreach{dp =>
      sums(dp.bucket) += dp.value
    }
    sums(0).sum
  }

  def fastRegularSum(args: ReadAndSumArgs) {
    val bucketSums = new Array[Float](args.nBuckets)
    val file = args.inputFile
    val raf = new RandomAccessFile(file, "rw")
    val fc = raf.getChannel
    val maxBytes = file.length
    val mbb = fc.map(MapMode.READ_WRITE, 0, maxBytes)
    mbb.load()
    val t = BB2Itr.makeBB2[DataPointIm](mbb)
    val start = System.nanoTime()
    var p = 0
    while( p < maxBytes) {
      t.setBuffer(mbb, p)
      bucketSums(t.bucket) += t.value
      p += 8
    }
    val end = System.nanoTime()
    println("fast regular sum\t" + ((end - start).toDouble / 1e9))
    println("result = " + bucketSums(0))
    println()
  }


  def fastBBKahanSum(args: ReadAndSumArgs) {
    val bbsum = ByteBuffer.allocate(args.nBuckets * 8)
    val sums = BB2Itr.indexedBB2[BBKahanSummer](bbsum)
    val (mbb, maxBytes) = BB2Itr.mmap(args.inputFile, load = true)
    val t = BB2Itr.makeBB2[DataPointIm](mbb)
    val start = System.nanoTime()
    var p = 0
    while( p < maxBytes) {
      t.setBuffer(mbb, p)
      sums(t.bucket) += t.value
      p += 8
    }
    val end = System.nanoTime()
    println("fast bb kahan sum\t" + ((end - start).toDouble / 1e9))
    println("result = " + sums(0).sum)
    println()
  }


  def fastPOJOKahanSum(args: ReadAndSumArgs) {
    val sums = new Array[POJOKahanSummer](args.nBuckets)
    (0 until args.nBuckets).foreach{idx => sums(idx) = new POJOKahanSummer}
    val (mbb, maxBytes) = BB2Itr.mmap(args.inputFile, load = true)
    val t = BB2Itr.makeBB2[DataPointIm](mbb)
    val start = System.nanoTime()
    var p = 0
    while( p < maxBytes) {
      t.setBuffer(mbb, p)
      sums(t.bucket) += t.value
      p += 8
    }
    val end = System.nanoTime()
    println("fast pojo kahan sum\t" + ((end - start).toDouble / 1e9))
    println("result = " + sums(0).sum)
    println()
  }




}


class ReadAndSumArgs extends FieldArgs {
  var inputFile: File = _
  var nBuckets: Int = 1e4.toInt
}