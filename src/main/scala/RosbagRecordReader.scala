/* Copyright 2017 The Authors. All Rights Reserved.
See the AUTHORS file distributed with
this work for additional information regarding The Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package de.valtech.foss

import scala.collection.JavaConverters._

import java.io.IOException
import java.io.FileInputStream
import java.lang.Math.toIntExact
import java.nio.ByteOrder._
import java.nio.ByteBuffer

import org.apache.hadoop.fs.FSDataInputStream
import org.apache.hadoop.io._
import org.apache.hadoop.io.compress.CompressionCodecFactory
import org.apache.hadoop.mapreduce.{InputSplit, RecordReader, TaskAttemptContext}
import org.apache.hadoop.mapreduce.lib.input.FileSplit

import de.valtech.foss.proto.RosbagIdxOuterClass.RosbagIdx

class RosbagBytesRecordReader extends RecordReader[LongWritable, BytesWritable] {

  private var fileInputStream: FSDataInputStream = null
  private var recordKey: LongWritable = null
  private var recordValue: BytesWritable = null

  private var splitStart: Long = 0L
  private var splitEnd: Long = 0L
  private var currentPosition: Long = 0L
  //private var idx: Array[Long] = Array[Long]()
  private var idx: Array[java.lang.Long] = Array[java.lang.Long]()

  override def close() {
    if (fileInputStream != null) {
      fileInputStream.close()
    }
  }

  override def getCurrentKey: LongWritable = {
    recordKey
  }

  override def getCurrentValue: BytesWritable = {
    recordValue
  }

  override def getProgress: Float = {
    splitStart match {
      case x if x == splitEnd => 0.0.toFloat
      case _ => Math.min(
        ((currentPosition - splitStart) / (splitEnd - splitStart)).toFloat, 1.0
      ).toFloat
    }
  }

  var id: org.apache.hadoop.mapreduce.TaskAttemptID = null

  override def initialize(inputSplit: InputSplit, context: TaskAttemptContext) {
    id = context.getTaskAttemptID()
    val rosChunkIdx = RosbagInputFormat.getRosChunkIdx(context)
    idx = RosbagIdx.parseFrom(new FileInputStream(rosChunkIdx)).getArrayList.asScala.toArray

    val fileSplit = inputSplit.asInstanceOf[FileSplit]
    splitStart = idx.find(e=>e>fileSplit.getStart).get
    splitEnd = idx.find(e=>e>fileSplit.getStart + fileSplit.getLength).getOrElse(fileSplit.getStart + fileSplit.getLength).asInstanceOf[Long]

    val file = fileSplit.getPath
    val conf = context.getConfiguration
    val codec = new CompressionCodecFactory(conf).getCodec(file)
    if (codec != null) {
      throw new IOException("RosbagRecordReader does not support reading compressed files ... yet!")
    }

    val fs = file.getFileSystem(conf)
    fileInputStream = fs.open(file)
    fileInputStream.seek(splitStart)
    currentPosition = splitStart
  }

  override def nextKeyValue(): Boolean = {
    if (recordKey == null)
      recordKey = new LongWritable()
    if (recordValue == null)
      recordValue = new BytesWritable()

    val nextPosition = idx.find(e=>e>currentPosition).getOrElse(splitEnd.longValue).asInstanceOf[Long]
    if(nextPosition == splitEnd)
      recordKey.set(idx.size)
    else
      recordKey.set(idx.indexOf(nextPosition))

    if (currentPosition < splitEnd) {
      val buffSize = toIntExact(nextPosition - currentPosition)
      val b = Array.ofDim[Byte](buffSize)
      fileInputStream.readFully(b)
      recordValue.set(b,0,b.size)
      currentPosition = nextPosition
      return true
    }
    false
  }
}



class RosbagMapRecordReader extends RecordReader[LongWritable, MapWritable] {

  private var fileInputStream: FSDataInputStream = null
  private var recordKey: LongWritable = null
  private var recordValue: MapWritable = null

  private var splitStart: Long = 0L
  private var splitEnd: Long = 0L
  private var currentPosition: Long = 0L
  //private var idx: Array[Long] = Array[Long]()
  private var idx: Array[java.lang.Long] = Array[java.lang.Long]()

  private val queue = scala.collection.mutable.Queue[BagRecord]()

  override def close() {
    if (fileInputStream != null) {
      fileInputStream.close()
    }
  }

  override def getCurrentKey: LongWritable = {
    recordKey
  }

  override def getCurrentValue: MapWritable = {
    recordValue
  }

  override def getProgress: Float = {
    splitStart match {
      case x if x == splitEnd => 0.0.toFloat
      case _ => Math.min(
        ((currentPosition - splitStart) / (splitEnd - splitStart)).toFloat, 1.0
      ).toFloat
    }
  }

  var id: org.apache.hadoop.mapreduce.TaskAttemptID = null

  override def initialize(inputSplit: InputSplit, context: TaskAttemptContext) {
    id = context.getTaskAttemptID()
    val rosChunkIdx = RosbagInputFormat.getRosChunkIdx(context)
    idx = RosbagIdx.parseFrom(new FileInputStream(rosChunkIdx)).getArrayList.asScala.toArray

    val fileSplit = inputSplit.asInstanceOf[FileSplit]
    splitStart = idx.find(e=>e>fileSplit.getStart).get
    splitEnd = idx.find(e=>e>fileSplit.getStart + fileSplit.getLength).getOrElse(fileSplit.getStart + fileSplit.getLength).asInstanceOf[Long]

    val file = fileSplit.getPath
    val conf = context.getConfiguration
    val codec = new CompressionCodecFactory(conf).getCodec(file)
    if (codec != null) {
      throw new IOException("RosbagRecordReader does not support reading compressed files ... yet!")
    }

    val fs = file.getFileSystem(conf)
    fileInputStream = fs.open(file)
    fileInputStream.seek(splitStart)
    currentPosition = splitStart
  }

  def serialize_header(h:Header) = {
    val hwritable = new MapWritable()
    h.fields.foreach(x => (x._1, x._2) match {
      case (k:String, v:String) => hwritable.put(new Text(k),new Text(v))
      case (k:String, v:Int) => hwritable.put(new Text(k),new LongWritable(v))
      case (k:String, v:Long) => hwritable.put(new Text(k),new LongWritable(v))
      case (k:String, v:Byte) => hwritable.put(new Text(k),new LongWritable(v))
      case (k:String, v:Array[Byte]) => hwritable.put(new Text(k),new BytesWritable(v))
      case _ => throw new IOException(s"RosbagRecordReader unexpected type in header ${x}")
    })
    hwritable
  }

  def dequeue_record() = {
    recordKey.set(fileInputStream.getPos() - queue.size)
    recordValue.clear
    val r = queue.dequeue
    recordValue.put(new Text("header"), serialize_header(r.header))
    r.data.fields match {
      case v:Array[Byte] => recordValue.put(new Text("data"), new BytesWritable(v))
      case h:Header => recordValue.put(new Text("data"), serialize_header(h))
      case v:List[(Long, Long)] => {
        // java has wrong variance of array, in scala Array is invariant in type T
        val a = Array.ofDim[ArrayWritable](v.size)
        for(i <- 0 to v.size - 1) {
          val tw = new ArrayWritable(classOf[LongWritable])
          tw.set(Array(new LongWritable(v(i)._1), new LongWritable(v(i)._2)).asInstanceOf[Array[Writable]])
          a(i) = tw
        }
        val aa = new ArrayWritable(classOf[ArrayWritable])
        aa.set(a.asInstanceOf[Array[Writable]])
        recordValue.put(new Text("data"), aa)
      }
      case None => recordValue.put(new Text("data"), NullWritable.get())
      case _ => throw new IOException(s"RosbagRecordReader unexpected type in data ${r.data.fields} of the record ${r}")
    }
  }

  def enqueue_record(r:BagRecord) = {
    if(r.header.fields("op") == 0x05)
      for(i <- r.data.fields.asInstanceOf[List[BagRecord]])
        queue += i
    else
      queue += r
  }

  override def nextKeyValue(): Boolean = {
    if (recordKey == null)
      recordKey = new LongWritable()
    if (recordValue == null)
      recordValue = new MapWritable()

    if(!queue.isEmpty) {
      dequeue_record
      return true
    }

    val nextPosition = idx.find(e=>e>currentPosition).getOrElse(splitEnd.longValue).asInstanceOf[Long]

    if (currentPosition < splitEnd) {
      val buffSize = toIntExact(nextPosition - currentPosition)
      val b = Array.ofDim[Byte](buffSize)
      fileInputStream.readFully(b)
      val p:RosbagParser = new RosbagParser(ByteBuffer.wrap(b).order(LITTLE_ENDIAN))
      var r = p.read_record;
      while(r.isDefined){ enqueue_record(r.get); r = p.read_record }
      currentPosition = nextPosition
      if(!queue.isEmpty)
        dequeue_record
      return true
    }
    false
  }
}
