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

import scala.io.Source
import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.{BytesWritable, LongWritable, MapWritable}
import org.apache.hadoop.mapreduce.{InputSplit, JobContext, RecordReader, TaskAttemptContext}
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat

object RosbagInputFormat {
  def getRosChunkIdx(context: JobContext): String = {
    context.getConfiguration.get("RosbagInputFormat.chunkIdx")
  }
  def getBlockSize(context: JobContext): Long = {
    context.getConfiguration.get("dfs.blocksize").toLong
  }
}

class RosbagBytesInputFormat
  extends FileInputFormat[LongWritable, BytesWritable] {

  private var rosChunkIdx = ""
  private var recordLength = -1L

  override def isSplitable(context: JobContext, filename: Path): Boolean = {
    rosChunkIdx = RosbagInputFormat.getRosChunkIdx(context)
    recordLength = RosbagInputFormat.getBlockSize(context)
    true
  }

  override def computeSplitSize(blockSize: Long, minSize: Long, maxSize: Long): Long = {
    val defaultSize = super.computeSplitSize(blockSize, minSize, maxSize)
    defaultSize
  }

  override def createRecordReader(split: InputSplit, context: TaskAttemptContext)
      : RecordReader[LongWritable, BytesWritable] = {
    new RosbagBytesRecordReader
  }
}



class RosbagMapInputFormat
  extends FileInputFormat[LongWritable, MapWritable] {

  private var rosChunkIdx = ""
  private var recordLength = -1L

  override def isSplitable(context: JobContext, filename: Path): Boolean = {
    rosChunkIdx = RosbagInputFormat.getRosChunkIdx(context)
    recordLength = RosbagInputFormat.getBlockSize(context)
    true
  }

  override def computeSplitSize(blockSize: Long, minSize: Long, maxSize: Long): Long = {
    val defaultSize = super.computeSplitSize(blockSize, minSize, maxSize)
    defaultSize
  }

  override def createRecordReader(split: InputSplit, context: TaskAttemptContext)
      : RecordReader[LongWritable, MapWritable] = {
    new RosbagMapRecordReader
  }
}
