package com.typesafe.akka.http2

import akka.util.ByteString
import java.lang.IllegalArgumentException

/* HTTP 2 Frame
 +-----------------------------------------------+
 |                 Length (24)                   |
 +---------------+---------------+---------------+
 |   Type (8)    |   Flags (8)   |
 +-+-------------+---------------+-------------------------------+
 |R|                 Stream Identifier (31)                      |
 +=+=============================================================+
 |                   Frame Payload (0...)                      ...
 */

abstract class Http2Frame(val length: Int,
                          val frameType: Byte,
                          val flags: Byte,
                          val payload: ByteString) {
  if (length > 16777216) throw new IllegalArgumentException(s"HTTP 2 frame length cannot exceed 16777216 bytes.  Given: $length")
  def identifier: Int
  // def toBytes: Array[Byte] = ByteString(length) ++ ByteString(frameType)
}

object Http2Frame {
  final case class Data(identifier: Int,
                        data: ByteString,
                        endStream: Boolean = false,
                        padding: Option[Int] = None) extends Http2Frame(length = data.length + padding.map(_ + 1).getOrElse(0),
                                                                        frameType = 0x0,
                                                                        flags = Data.flags(endStream,padding.isDefined),
                                                                        payload = padding.map(p => ByteString(p.toByte) ++ data ++ ByteString(Array.fill[Byte](p)(0))).getOrElse(data)) {
    padding.foreach(x => if (x > 255) throw new IllegalArgumentException(s"HTTP 2 Data frame padding cannot exceed 255 bytes.  Given: $x") )
  }

  object Data {
    private final val zero:Byte = 0x00
    private final val endStreamFlag:Byte = 0x01
    private final val paddingFlag:Byte = 0x04

    def flags(endStream: Boolean,
              padded: Boolean):Byte =
                ((if (endStream) endStreamFlag else zero) |
                (if (padded) paddingFlag else zero)).toByte
  }
}

/* HTTP 2 Settings
 +-------------------------------+
 |       Identifier (16)         |
 +-------------------------------+-------------------------------+
 |                        Value (32)                             |
 +---------------------------------------------------------------+
 */

abstract class Http2Settings(ident:Int /* Only uses 16 bits */,
                             v:Long /* Only uses 32 bits */) {

  if (ident > 65536 || ident < 0) throw new IllegalArgumentException(s"identifier needs to be between 0 and 65536.  Given: $ident")
  if (v > 4294967296L || v < 0L) throw new IllegalArgumentException(s"value needs to be between 0 and 4294967296.  Given: $v")

  final def identifier:Int = ident
  final def value: Long = v
}

object Http2Settings {
  final case class HeaderTableSize(size:Long = 4096) extends Http2Settings(0x1,size)
  final case class EnablePush(enabled:Boolean = true) extends Http2Settings(0x2,if (enabled) 1 else 0)
  final case class MaxConcurrentStreams(max:Long) extends Http2Settings(0x3,max)
  final case class InitialWindowSize(size:Int = 65535) extends Http2Settings(0x4,size) {
    if (size > 2147483647 /* 2^31 - 1 */) throw new IllegalArgumentException("Flow control error")
  }
  final case class MaxFrameSize(size:Long) extends Http2Settings(0x5,size)
  final case class MaxHeaderListSize(size:Long) extends Http2Settings(0x6,size)
}