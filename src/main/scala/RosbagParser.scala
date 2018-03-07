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
import scala.collection.mutable.Map
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import Console.{GREEN, RED, RESET}
import scala.language.reflectiveCalls

import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel.MapMode._
import java.nio.ByteOrder._
import java.nio.ByteBuffer

sealed trait Bag
case class BagVersion(ver:String) extends Bag
case class BagRecord(header:Header, data:Data) extends Bag
case class Header(len:Int, fields:Map[String,Any])
case class Data(len:Int, fields:Any)

class RosbagParser(f: ByteBuffer) {

  def as_ascii(b:ByteBuffer, k:Int): String = {
    val bytes = Array.ofDim[Byte](k)
    b.get(bytes)
    new String(bytes)
  }

  def as_buffer(a:Any): ByteBuffer = {
    ByteBuffer.wrap(a.asInstanceOf[Array[Byte]]).order(LITTLE_ENDIAN)
  }

  def decode_x07(h: Header): BagRecord = {
    h.fields("conn")  = as_buffer(h.fields("conn")).getInt
    //val len = f.getInt
    val data = read_header()
    BagRecord(h, Data(data.len, data))
  }

  def decode_x06(h: Header): BagRecord = {
    h.fields("ver")  = as_buffer(h.fields("ver")).getInt
    h.fields("count")= as_buffer(h.fields("count")).getInt
    h.fields("chunk_pos")= as_buffer(h.fields("chunk_pos")).getLong
    h.fields("start_time")= as_buffer(h.fields("start_time")).getLong
    h.fields("end_time")= as_buffer(h.fields("end_time")).getLong
    val len = f.getInt
    val data = ListBuffer[(Long, Long)]()
    for (k <- 0 to (h.fields("count").asInstanceOf[Int]-1)) {
      val conn   = f.getInt
      val count = f.getInt
      data += (conn.toLong -> count.toLong)
    }
    BagRecord(h, Data(len, data.toList))
  }

  def decode_x05(h: Header): BagRecord = {
    h.fields("size")  = as_buffer(h.fields("size")).getInt
    if (h.fields("compression").asInstanceOf[Array[Byte]] == "none".getBytes)
      throw new Exception("0x05 it is compressed")
    val len = f.getInt
    val b = Array.ofDim[Byte](len)
    f get b
    val data = ListBuffer[BagRecord]()
    val p:RosbagParser = new RosbagParser(ByteBuffer.wrap(b).order(LITTLE_ENDIAN))
    var r = p.read_record;
    while(r.isDefined){ data += r.get; r = p.read_record }
    BagRecord(h, Data(len, data.toList))
  }

  def decode_x04(h: Header): BagRecord = {
    h.fields("ver")  = as_buffer(h.fields("ver")).getInt
    h.fields("conn") = as_buffer(h.fields("conn")).getInt
    h.fields("count")= as_buffer(h.fields("count")).getInt
    val len = f.getInt
    val data = ListBuffer[(Long, Long)]()
    for (k <- 0 to h.fields("count").asInstanceOf[Int] - 1) {
      val time   = f.getLong
      val offset = f.getInt
      data += (time.toLong -> offset.toLong)
    }
    BagRecord(h, Data(len, data.toList))
  }

  def decode_x03(h: Header): BagRecord = {
    h.fields("conn_count") = as_buffer(h.fields("conn_count")).getInt
    h.fields("chunk_count") = as_buffer(h.fields("chunk_count")).getInt
    h.fields("index_pos") = as_buffer(h.fields("index_pos")).getLong
    val len = f.getInt
    val b = Array.ofDim[Byte](len)
    f get b
    BagRecord(h, Data(len, b))
  }

  def decode_x02(h:Header) = {
    h.fields("time") = as_buffer(h.fields("time")).getLong
    h.fields("conn") =  as_buffer(h.fields("conn")).getInt
    val len = f.getInt
    val b = Array.ofDim[Byte](len)
    f get b
    BagRecord(h, Data(len, b))
  }

  val decode = Map[Byte, (Header) => BagRecord]()
  decode(0x07) = decode_x07
  decode(0x06) = decode_x06
  decode(0x05) = decode_x05
  decode(0x04) = decode_x04
  decode(0x03) = decode_x03
  decode(0x02) = decode_x02

  def read_version(): String = {
    as_ascii(f, 13)
  }

  def read_header(): Header = {
    val res = Map[String,Any]()
    var idx = 0
    val hl = f.getInt
    while(idx < hl){
      val b = Array.ofDim[Byte](f.getInt)
      f get b
      val (k,v) = b.splitAt(b.indexOf('=')+1)
      res += (new String(k,0,k.size-1) -> v)
      idx = idx+4+b.size
    }
    if(res contains "op"){
      val op = res("op").asInstanceOf[Array[Byte]].head
      res("op") = op
    }
    Header(hl, res)
  }

  def read_record(): Option[BagRecord] = {
    if(!f.hasRemaining | f.remaining < 4) return None
    val h = read_header()
    if(h.len == 0) return None
    Some(decode(h.fields("op").asInstanceOf[Byte])(h))
  }

  def read_connections(header:Header, topics:List[String]):
      Map[Int,Bag] = {
    val res = Map[Int,Bag]()
    for(i <- List.range(0,header.fields("conn_count").asInstanceOf[Int])){
      val r = read_record().get
      if(r.header.fields("op") != 0x07)
        throw new Exception(s"0x07 expected but found ${r.header.fields("op")}")
      if(topics.isEmpty | topics.contains(r.header.fields("topic")))
        res(r.header.fields("conn").asInstanceOf[Int]) = r
    }
    res
  }

  def read_chunk_infos(c:Map[Int,Bag]):
    scala.collection.mutable.SortedSet[Long] = {
    var r = read_record()
    val ss = scala.collection.mutable.SortedSet[Long]()
    while(r.isDefined) {
      ss += r.get.header.fields("chunk_pos").asInstanceOf[Long]
      r = read_record()
    }
    ss
  }

}
