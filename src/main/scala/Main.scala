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
import java.io.FileOutputStream
import java.nio.channels.FileChannel.MapMode._
import java.nio.ByteOrder._
import java.nio.ByteBuffer

import de.valtech.foss.proto.RosbagIdxOuterClass.RosbagIdx

object Main extends App {
  def help() = {
    Console.err.printf(s"""
${RESET}${GREEN}Usage:
	--file <ros.bag> file to process
	--version print Rosbag version and exit
	--offset <offset> --number <records> Seek at offset < 1073741824 and read the specified number of records
${RESET}By default will just create the protobuf idx file needed for configuration.\n\n""")
    sys.exit(0)
  }

  val pargs = Map[String,AnyRef]()
  def process_cli(args: List[String]) :Boolean = args match {
    case Nil => true // parse success
    case "-v" :: rest => pargs += ("version" -> Some(true)); process_cli(rest)
    case "--version" :: rest => pargs += ("version" -> Some(true)); process_cli(rest)
    case "-f" :: x :: rest => pargs += ("file" -> x); process_cli(rest)
    case "--file" :: x :: rest => pargs += ("file" -> x); process_cli(rest)
    case "-n" :: x :: rest => pargs += ("number" -> Some(x.toInt)); process_cli(rest)
    case "--number" :: x :: rest => pargs += ("number" -> Some(x.toInt)); process_cli(rest)
    case "-o" :: x :: rest => pargs += ("offset" -> Some(x.toInt)); process_cli(rest)
    case "--offset" :: x :: rest => pargs += ("offset" -> Some(x.toInt)); process_cli(rest)
    case "-h" :: rest => help(); false
    case "--help" :: rest => help(); false
    case _ => Console.err.printf(s"${RESET}${RED}Unknown argument " + args.head); false
  }
  process_cli(args.toList)

  def use[T <: { def close() }]
    (resource: T)
    (code: T ⇒ Unit) =
    try
      code(resource)
    finally
      resource.close()

  pargs("file") match {
    case f:String => process()
    case _ => help()
  }

  def process(): Unit = {
    val fin = new File(pargs("file").asInstanceOf[String])
    use(new FileInputStream(fin)) { stream => {
      //printf("min: %s\n", Math.min(1073741824, fin.length) )
      val buffer = stream.getChannel.map(READ_ONLY, 0, Math.min(1073741824, fin.length)).order(LITTLE_ENDIAN)
      val p:RosbagParser = new RosbagParser(buffer)
      val version = p.read_version()
      val h = p.read_record().get
      if(pargs contains "version") {
        printf("%s\n%s\n\n", version, h)
        return
      }
      if(pargs contains "number"){
        buffer position pargs.getOrElse("offset",None).asInstanceOf[Option[Int]].getOrElse(0)
        for(i <- List.range(0,pargs("number").asInstanceOf[Option[Int]].getOrElse(0)))
          println(p.read_record)
        return
      }
      val idxpos = h.header.fields("index_pos").asInstanceOf[Long]
      //printf("idxpos: %s %s\n", idxpos, Math.min(1073741824, fin.length) )
      val b = stream.getChannel.map(READ_ONLY, idxpos, Math.min(1073741824, fin.length - idxpos)).order(LITTLE_ENDIAN)
      val pp:RosbagParser = new RosbagParser(b)
      val c = pp.read_connections(h.header, Nil)
      val chunk_idx = pp.read_chunk_infos(c)
      Console.err.printf(s"""${RESET}${GREEN}Found: """
          + chunk_idx.size
          +s""" chunks\n${RESET}It should be the same number reported by rosbag tool.\nIf you encounter any issues try reindexing your file and submit an issue.
          ${RESET}\n""")
      val fout = new FileOutputStream(pargs("file").asInstanceOf[String] + ".idx.bin")
      val builder = RosbagIdx.newBuilder
      for(i <- chunk_idx) builder.addArray(i)
      builder.build().writeTo(fout)
      fout.close()
      //printf("[%s]\n",chunk_idx.toArray.mkString(","))
    }}
  }
}
