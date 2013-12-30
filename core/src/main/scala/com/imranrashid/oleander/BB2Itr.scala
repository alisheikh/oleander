package com.imranrashid.oleander

import java.io.{RandomAccessFile, File}
import java.nio.channels.FileChannel.MapMode
import scala.reflect.ClassTag
import java.nio.ByteBuffer

/**
 *
 */
object BB2Itr {
  def fromMemMappedFile[T <: BB2: ClassTag](file: File): Iterator[T] = {
    val raf = new RandomAccessFile(file, "rw")
    val fc = raf.getChannel
    val maxBytes = file.length
    val mbb = fc.map(MapMode.READ_WRITE, 0, maxBytes)
    val t = makeBB2[T](mbb)
    (0 until maxBytes.toInt by t.numBytes).iterator.map{ idx =>
      t.setBuffer(mbb, idx)
      t
    }
  }

  def bbAsTraversable[T <: BB2](bb: ByteBuffer, obj: T)= {
    new BB2Traversable(bb, 0, bb.limit(), obj)
  }

  class BB2Traversable[T <: BB2](bb: ByteBuffer, start: Int, length: Int, o: T) extends Traversable[T] {
    def foreach[R](f: T => R){
      (start to (start + length) by o.numBytes).foreach{idx =>
        o.setBuffer(bb, idx)
        f(o)
      }
    }
  }

  def makeBB2[T <: BB2: ClassTag](bb: ByteBuffer): T = {
    val cls = implicitly[ClassTag[T]].runtimeClass
    val ctor = cls.getConstructor(Array(classOf[ByteBuffer], Integer.TYPE): _*)
    ctor.newInstance(Array(bb, new java.lang.Integer(0)): _*).asInstanceOf[T]
  }

  def indexedBB2[T <: BB2: ClassTag](bb: ByteBuffer): IndexedBb2[T] = {
    val t = makeBB2[T](bb)
    new IndexedBb2[T](bb, 0, bb.limit(), t)
  }

}

class IndexedBb2[T <: BB2](bb: ByteBuffer, start: Int, length: Int, o: T) {

  def apply(idx: Int): T = {
    o.setBuffer(bb, start + idx * o.numBytes)
    o
  }
}