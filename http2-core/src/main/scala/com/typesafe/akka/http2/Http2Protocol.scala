package com.typesafe.akka.http2

import akka.util.ByteString
import java.lang.IllegalArgumentException

final class StreamIdentifier private(val underlying:Int) extends AnyVal {
  def toInt:Int = underlying
  def toByteString:ByteString = ByteString(underlying)
}

object StreamIdentifier {
  def apply(in:Long):StreamIdentifier = {
    require(in <= 2147483648L && in >= 0,s"HTTP 2 frame streamIdentifier must be in the range [0..2147483648] bytes.  Given: $in")
    new StreamIdentifier(in.intValue)
  }

  def unapply(in:Any):Option[StreamIdentifier] = in match {
    case x:Long => Some(apply(x))
    case x:Int => Some(apply(x))
    case _ => None
  }
}

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
final case class Http2Frame(streamIdentifier: StreamIdentifier,
                            length: Int,
                            frameType: Byte,
                            flags: Byte,
                            payload: ByteString) {
  require(length <= 16777216 && length >= 0, s"HTTP 2 frame length must be in the range [0..16777216] bytes.  Given: $length")
}

sealed trait ToHttp2Frame {
  def toFrame: Http2Frame
  def streamIdentifier: StreamIdentifier
}

sealed trait FrameDef {
  def frameType:Byte
}

object Http2Frame {

 /* Data
   +---------------+
   |Pad Length? (8)|
   +---------------+-----------------------------------------------+
   |                            Data (*)                         ...
   +---------------------------------------------------------------+
   |                           Padding (*)                       ...
   +---------------------------------------------------------------+
  */
  final case class Data(streamIdentifier: StreamIdentifier,
                        data: ByteString,
                        endStream: Boolean = false,
                        padding: Option[Int] = None) extends ToHttp2Frame {

    padding.foreach(x => require(x <= 255 && x >= 0, s"HTTP 2 Data frame padding must be in the range [0..255] bytes.  Given: $x") )

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = data.length + padding.map(_ + 1).getOrElse(0),
                                        frameType = Data.frameType,
                                        flags = Data.flags(endStream,padding.isDefined),
                                        payload = padding.map(p => ByteString(p.toByte) ++ data ++ ByteString(Array.fill[Byte](p)(0))).getOrElse(data))
  }

  object Data extends FrameDef {
    final val endStreamFlag:Byte = 0x01
    final val paddingFlag:Byte = 0x08

    private def flags(endStream: Boolean,
                      padded: Boolean):Byte = Flags.Zero.addFlag(endStream,endStreamFlag).addFlag(padded,paddingFlag).toByte

    val frameType:Byte = 0x00
  }

  /*
   +---------------+
   |Pad Length? (8)|
   +-+-------------+-----------------------------------------------+
   |E|                 Stream Dependency? (31)                     |
   +-+-------------+-----------------------------------------------+
   |  Weight? (8)  |
   +-+-------------+-----------------------------------------------+
   |                   Header Block Fragment (*)                 ...
   +---------------------------------------------------------------+
   |                           Padding (*)                       ...
   +---------------------------------------------------------------+
  */
  final case class Header(streamIdentifier: StreamIdentifier,
                          fragment: ByteString,
                          endHeaders: Boolean = true,
                          endStream: Boolean = false,
                          priority: Option[Header.Priority] = None,
                          padding: Option[Int] = None) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = fragment.length + padding.map(_ + 1).getOrElse(0) + (if (priority.isDefined) 5 else 0),
                                        frameType = Header.frameType,
                                        flags = Header.flags(endStream,endHeaders,padding.isDefined,priority.isDefined),
                                        payload = padding.map(p => ByteString(p.toByte) ++ fragment ++ ByteString(Array.fill[Byte](p)(0))).getOrElse(fragment))

  }

  object Header extends FrameDef {
    private val endStreamFlag:Byte = 0x1
    private val endHeadersFlag:Byte = 0x4
    private val paddedFlag:Byte = 0x8
    private val priorityFlag:Byte = 0x20

    private def flags(endStream: Boolean,
                      endHeaders: Boolean,
                      padded: Boolean,
                      priority: Boolean):Byte = Flags.Zero
                                                  .addFlag(endStream,endStreamFlag)
                                                  .addFlag(endHeaders,endHeadersFlag)
                                                  .addFlag(priority,priorityFlag)
                                                  .addFlag(padded,paddedFlag).toByte

    case class Priority(streamDependency:StreamIdentifier,
                        weight:Byte,
                        exclusive:Boolean = false)

    val frameType:Byte = 0x01
  }

  /*
   +-+-------------------------------------------------------------+
   |E|                  Stream Dependency (31)                     |
   +-+-------------+-----------------------------------------------+
   |   Weight (8)  |
   +-+-------------+
  */
  final case class Priority(streamIdentifier: StreamIdentifier,
                            streamDependency: StreamIdentifier,
                            weight:Byte,
                            exclusive:Boolean = false) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = 5,
                                        frameType = Priority.frameType,
                                        flags = Flags.zero,
                                        payload = ByteString(if (exclusive) 0x80000000 | streamDependency.toInt else streamDependency.toInt) ++ ByteString(weight))

  }

  object Priority extends FrameDef {
    val frameType:Byte = 0x02
  }


  /*
   +---------------------------------------------------------------+
   |                        Error Code (32)                        |
   +---------------------------------------------------------------+
  */
  final case class RstStream(streamIdentifier: StreamIdentifier,
                             errorCode:Int) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = 4,
                                        frameType = RstStream.frameType,
                                        flags = Flags.zero,
                                        payload = ByteString(errorCode))

  }

  object RstStream extends FrameDef {
    val frameType:Byte = 0x03
  }

  /* Settings
   +-------------------------------+
   |       Identifier (16)         |
   +-------------------------------+-------------------------------+
   |                        Value (32)                             |
   +---------------------------------------------------------------+
   */
  abstract class Settings(streamIdentifier: StreamIdentifier,
                          identifier:Int /* Only uses 16 bits */,
                          value:Long /* Only uses 32 bits */,
                          ack:Boolean = false) extends ToHttp2Frame {

    require(identifier < 65536 && identifier >= 0,s"HTTP 2 Settings identifier must be in a range of [0..65535].  Given: $identifier")
    require(value >=0 && value < 4294967296L, s"HTTP 2 Settings value must be in a range of [0..4294967296]. Given: $value")

    def toFrame:Http2Frame = Http2Frame(streamIdentifier,
                                        length = 6,
                                        frameType = Settings.frameType,
                                        flags = Settings.flags(ack),
                                        payload = ByteString(identifier.toShort) ++ ByteString(value.toInt))
  }

  object Settings extends FrameDef {
    private final val ackFlag:Byte = 0x01

    val frameType:Byte = 0x04

    private def flags(ack:Boolean):Byte = Flags.Zero.addFlag(ack,ackFlag).toByte

    final case class HeaderTableSize(streamIdentifier: StreamIdentifier,size:Long = 4096, ack:Boolean = false) extends Settings(streamIdentifier, 0x1,size,ack)
    final case class EnablePush(streamIdentifier: StreamIdentifier,enabled:Boolean = true, ack:Boolean = false) extends Settings(streamIdentifier, 0x2,if (enabled) 1 else 0,ack)
    final case class MaxConcurrentStreams(streamIdentifier: StreamIdentifier,max:Long, ack:Boolean = false) extends Settings(streamIdentifier, 0x3,max,ack)
    final case class InitialWindowSize(streamIdentifier: StreamIdentifier,size:Int = 65535, ack:Boolean = false) extends Settings(streamIdentifier, 0x4,size,ack) {
      if (size > 2147483647 /* 2^31 - 1 */) throw new IllegalArgumentException("Flow control error")
    }
    final case class MaxFrameSize(streamIdentifier: StreamIdentifier,size:Long, ack:Boolean = false) extends Settings(streamIdentifier, 0x5,size,ack)
    final case class MaxHeaderListSize(streamIdentifier: StreamIdentifier,size:Long, ack:Boolean = false) extends Settings(streamIdentifier, 0x6,size,ack)
  }

  /*
   +---------------+
   |Pad Length? (8)|
   +-+-------------+-----------------------------------------------+
   |R|                  Promised Stream ID (31)                    |
   +-+-----------------------------+-------------------------------+
   |                   Header Block Fragment (*)                 ...
   +---------------------------------------------------------------+
   |                           Padding (*)                       ...
   +---------------------------------------------------------------+
  */
  final case class PushPromise(streamIdentifier: StreamIdentifier,
                               fragment: ByteString,
                               endHeaders: Boolean = true,
                               padding: Option[Int] = None) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = fragment.length + padding.map(_ + 1).getOrElse(0),
                                        frameType = PushPromise.frameType,
                                        flags = PushPromise.flags(endHeaders,padding.isDefined),
                                        payload = padding.map(p => ByteString(p.toByte) ++ fragment ++ ByteString(Array.fill[Byte](p)(0))).getOrElse(fragment))

  }

  object PushPromise extends FrameDef {
    private val endHeadersFlag:Byte = 0x4
    private val paddedFlag:Byte = 0x8

    private def flags(endHeaders: Boolean,
                      padded: Boolean):Byte = Flags.Zero
                                                  .addFlag(endHeaders,endHeadersFlag)
                                                  .addFlag(padded,paddedFlag).toByte

    val frameType:Byte = 0x05
  }

  /*
   +---------------------------------------------------------------+
   |                                                               |
   |                      Opaque Data (64)                         |
   |                                                               |
   +---------------------------------------------------------------+
  */
  final case class Ping(streamIdentifier: StreamIdentifier,
                        opaque:Long = 0L,
                        ack:Boolean = false) extends ToHttp2Frame {
    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = 8,
                                        frameType = Ping.frameType,
                                        flags = Ping.flags(ack),
                                        payload = ByteString(opaque))
  }

  object Ping extends FrameDef {
    private val ackFlag:Byte = 0x1

    private def flags(ack: Boolean):Byte = Flags.Zero
                                                  .addFlag(ack,ackFlag).toByte

    val frameType:Byte = 0x06
  }

  /*
   +-+-------------------------------------------------------------+
   |R|                  Last-Stream-ID (31)                        |
   +-+-------------------------------------------------------------+
   |                      Error Code (32)                          |
   +---------------------------------------------------------------+
   |                  Additional Debug Data (*)                    |
   +---------------------------------------------------------------+
  */
  final case class GoAway(streamIdentifier: StreamIdentifier,
                          lastStreamIdentifier: StreamIdentifier,
                          errorCode:Int,
                          debugData:ByteString) extends ToHttp2Frame {
    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = 8 + debugData.length,
                                        frameType = GoAway.frameType,
                                        flags = Flags.zero,
                                        payload = lastStreamIdentifier.toByteString ++ ByteString(errorCode) ++ debugData)
  }

  object GoAway extends FrameDef {
    val frameType:Byte = 0x07
  }


  /*
   +-+-------------------------------------------------------------+
   |R|              Window Size Increment (31)                     |
   +-+-------------------------------------------------------------+
  */
  final case class WindowUpdate(streamIdentifier: StreamIdentifier,
                                windowSizeIncrement:Int) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = 4,
                                        frameType = WindowUpdate.frameType,
                                        flags = Flags.zero,
                                        payload = ByteString(windowSizeIncrement))

  }

  object WindowUpdate extends FrameDef {
    val frameType:Byte = 0x08
  }


  /*
   +---------------------------------------------------------------+
   |                   Header Block Fragment (*)                 ...
   +---------------------------------------------------------------+
  */
  final case class Continuation(streamIdentifier: StreamIdentifier,
                                fragment:ByteString,
                                endHeaders:Boolean = true) extends ToHttp2Frame {

    def toFrame:Http2Frame = Http2Frame(streamIdentifier = streamIdentifier,
                                        length = fragment.length,
                                        frameType = Continuation.frameType,
                                        flags = Continuation.flags(endHeaders),
                                        payload = fragment)

  }

  object Continuation extends FrameDef {
    private val endHeadersFlag:Byte = 0x04

    private def flags(endHeaders: Boolean):Byte = Flags.Zero
                                                  .addFlag(endHeaders,endHeadersFlag).toByte

    val frameType:Byte = 0x09
  }







  private final case class Flags(underlying:Byte = Flags.zero) {
    def addFlag(flag:Boolean,mask:Byte):Flags = Flags((underlying | (if (flag) mask else Flags.zero)).toByte)
    def toByte:Byte = underlying
  }

  private object Flags {
    final val zero:Byte = 0x00
    final val Zero:Flags = Flags()
  }

}

