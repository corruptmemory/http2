/*
 * Copyright © 2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */
package com.typesafe.akka.http2

import akka.util.ByteString

object Http2Muxer {
  case class Muxed(accumulator: Vector[ByteString])
  case class MuxedFrame(accumulator: Vector[ByteString])
}

class Http2Muxer() {

  import Http2Frame._
  import Http2Muxer._

  /**
   * Muxes frames until they are all done, or initialWindowSize is reached.
   *
   * @param maxFrameSize SETTINGS_MAX_FRAME_SIZE (0x5): Indicates the size of the largest frame payload that the sender is willing
   *   to receive, in octets.
   * @param initialWindowSize SETTINGS_INITIAL_WINDOW_SIZE (0x4): Indicates the sender's initial window size (in octets) for
   *   stream-level flow control. The initial value is 216-1 (65,535) octets.
   * @param frames FRAME DEFINITION: This specification defines a number of frame types, each identified by a unique 8-bit type
   *   code. Each frame type serves a distinct purpose in the establishment and management either of the connection as a whole or
   *   of individual streams.
   * @param accumulator accumlates the frames for muxing.
   *
   * @return muxed case class
   */
  def muxFrames(
      maxFrameSize: Int,
      initialWindowSize: Int,
      frames: Vector[ToHttp2Frame],
      accumulator: Vector[ByteString]): Muxed = {

    frames.headOption match {
      case Some(frame: Data)         => Muxed(accumulator ++ muxData(maxFrameSize, frame, accumulator).accumulator)
      case Some(frame: Header)       => Muxed(accumulator ++ muxHeader(maxFrameSize, frame, accumulator).accumulator)
      case Some(frame: Priority)     => Muxed(accumulator ++ muxPriority(frame, accumulator).accumulator)
      case Some(frame: RstStream)    => Muxed(accumulator ++ muxRstStream(frame, accumulator).accumulator)
      case Some(frame: Settings)     => Muxed(accumulator ++ muxSettings(frame, accumulator).accumulator)
      case Some(frame: PushPromise)  => Muxed(accumulator ++ muxPushPromise(frame, accumulator).accumulator)
      case Some(frame: Ping)         => Muxed(accumulator ++ muxPing(frame, accumulator).accumulator)
      case Some(frame: GoAway)       => Muxed(accumulator ++ muxGoAway(frame, accumulator).accumulator)
      case Some(frame: WindowUpdate) => Muxed(accumulator ++ muxWindowUpadate(frame, accumulator).accumulator)
      case Some(frame: Continuation) => Muxed(accumulator) // will not happen
      case None                      => Muxed(accumulator)
    }
  }

  def muxHeader(
    maxFrameSize: Int,
    headerFrame: Header,
    accumulator: Vector[ByteString]): MuxedFrame = {

    val headerFragments: Vector[ByteString] = headerFrame.fragment.sliding(maxFrameSize).toVector
    headerFragments.size match {
      case 1 =>
        MuxedFrame(accumulator :+ headerFrame.toFrame.toByteString)
      case _ =>
        val continuations: MuxedFrame = muxContinuation(
          maxFrameSize,
          headerFrame.streamIdentifier,
          headerFragments.tail,
          accumulator)
        MuxedFrame((accumulator :+ headerFrame.toFrame.toByteString) ++ continuations.accumulator)
    }
  }

  private def muxContinuation(
    maxFrameSize: Int,
    streamIdentifier: StreamIdentifier,
    fragments: Vector[ByteString],
    accumulator: Vector[ByteString]): MuxedFrame = {

    val continuations: Vector[Continuation] = fragments.map(frag =>
      Continuation(streamIdentifier, frag, endHeaders = false))

    val updatedContinuations: Vector[Continuation] = continuations.updated(
      continuations.size,
      Continuation(streamIdentifier, continuations.last.fragment, endHeaders = true))

    MuxedFrame(updatedContinuations.map(_.toFrame.toByteString))
  }

  def muxData(
      maxFrameSize: Int,
      dataFrame: Data,
      accumulator: Vector[ByteString]): MuxedFrame = {

    val datas = dataFrame.data.sliding(maxFrameSize).map(
      data => Data(dataFrame.streamIdentifier, dataFrame.data).toFrame.toByteString).toVector

    MuxedFrame(datas)
  }

  def muxPriority(priorityFrame: Priority, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ priorityFrame.toFrame.toByteString)

  def muxRstStream(rstStream: RstStream, accumulator: Vector[ByteString]): MuxedFrame = MuxedFrame(accumulator :+ rstStream.toFrame.toByteString)

  def muxSettings(settingsFrame: Settings, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ settingsFrame.toFrame.toByteString)

  def muxPushPromise(pushPromiseFrame: PushPromise, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ pushPromiseFrame.toFrame.toByteString)

  def muxPing(pingFrame: Ping, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ pingFrame.toFrame.toByteString)

  def muxGoAway(goAwayFrame: GoAway, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ goAwayFrame.toFrame.toByteString)

  def muxWindowUpadate(windowUpdateFrame: WindowUpdate, accumulator: Vector[ByteString]): MuxedFrame =
    MuxedFrame(accumulator :+ windowUpdateFrame.toFrame.toByteString)
}

