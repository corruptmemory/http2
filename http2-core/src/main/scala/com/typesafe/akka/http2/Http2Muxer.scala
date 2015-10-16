/*
 * Copyright © 2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */
package com.typesafe.akka.http2

import akka.util.ByteString

import scala.annotation.tailrec

object Http2Muxer {
  case class Muxed(numberBytesWritten: Int, unusedFrames: Vector[ToHttp2Frame], accumulator: Vector[ByteString])
  case class MuxedFrame(accumulator: Vector[ByteString], windowSize: Int = 0)
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

    @tailrec
    def mux(windowSize: Int, frames: Vector[ToHttp2Frame], accumulator: Vector[ByteString]): Muxed = {
      frames.headOption match {
        case None =>
          Muxed(windowSize, frames, accumulator)
        case Some(frame: Data) if windowSize < initialWindowSize =>
          val muxedData: MuxedFrame = muxData(maxFrameSize, initialWindowSize - windowSize, frame, accumulator)
          val windowSizeSum: Int = windowSize + muxedData.windowSize
          if (muxedData.windowSize < frame.data.size) {
            assert(windowSizeSum == initialWindowSize)
            Muxed(windowSizeSum, frames, accumulator ++ muxedData.accumulator)
          } else {
            mux(windowSizeSum, frames.tail, accumulator ++ muxedData.accumulator)
          }
        case Some(frame: Data) =>
          Muxed(windowSize, frames, accumulator)
        case Some(frame: Header) =>
          val muxedHeader: MuxedFrame = muxHeader(maxFrameSize, frame, accumulator)
          Muxed(windowSize, frames, accumulator ++ muxedHeader.accumulator)
      }
    }
    mux(0, frames, accumulator)
  }

  def muxHeader(
    maxFrameSize: Int,
    headerFrame: Header,
    accumulator: Vector[ByteString]): MuxedFrame = {

    if (headerFrame.fragment.size <= maxFrameSize)
      MuxedFrame(accumulator :+ headerFrame.toFrame.toByteString)
    else {
      val fragmentChunk: ByteString = headerFrame.fragment.take(maxFrameSize)
      val headerChunk = Header(headerFrame.streamIdentifier, fragmentChunk, endHeaders = false)
      val continuationFrame: MuxedFrame = muxContinuation(
        maxFrameSize,
        headerFrame.streamIdentifier,
        headerFrame.fragment.drop(fragmentChunk.size),
        accumulator)
      MuxedFrame((accumulator :+ headerChunk.toFrame.toByteString) ++ continuationFrame.accumulator)
    }
  }

  private def muxContinuation(
    maxFrameSize: Int,
    streamIdentifier: StreamIdentifier,
    fragment: ByteString,
    accumulator: Vector[ByteString]): MuxedFrame = {

    val continuation = fragment.sliding(maxFrameSize).map(frag =>
      Continuation(streamIdentifier, frag, endHeaders = false)).toVector

    val fixedContinuation = continuation.updated(
      continuation.size,
      Continuation(streamIdentifier, continuation.last.fragment, endHeaders = true))

    val fixedContinuationToVectorOfByteString = fixedContinuation.map(_.toFrame.toByteString)


    MuxedFrame(fixedContinuationToVectorOfByteString)
  }

  def muxData(
      maxFrameSize: Int,
      initialWindowSize: Int,
      dataFrame: Data,
      accumulator: Vector[ByteString]): MuxedFrame = {

    @tailrec
    def mux(windowSize: Int, data: ByteString, accumulator: Vector[ByteString]): MuxedFrame = {
      val frameSizeMin: Int = math.min(maxFrameSize, initialWindowSize - windowSize)
      if (data.size <= frameSizeMin)
        MuxedFrame(accumulator :+ dataFrame.toFrame.toByteString, windowSize)
      else {
        val dataChunk: ByteString = data.take(frameSizeMin)
        mux(windowSize + frameSizeMin, data.drop(frameSizeMin),
          accumulator :+ Data(dataFrame.streamIdentifier, dataChunk).toFrame.toByteString)
      }
    }
    mux(initialWindowSize, dataFrame.data, accumulator)
  }
}

