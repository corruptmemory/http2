/*
 * Copyright © 2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */
package com.typesafe.akka.http2

import akka.util.ByteString

import scala.annotation.tailrec

object Http2Muxer {
  case class Muxed(numberBytesWritten: Int, unusedFrames: Vector[Http2Frame], accumulator: Vector[Http2Frame])
  case class MuxedFrame(windowSize: Int, accumulator: Vector[Http2Frame])
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
      frames: Vector[Http2Frame],
      accumulator: Vector[Http2Frame]): Muxed = {

    @tailrec
    def mux(windowSize: Int, frames: Vector[Http2Frame], accumulator: Vector[Http2Frame]): Muxed = {
      frames.headOption match {
        case None =>
          Muxed(windowSize, frames, accumulator)
        case Some(frame:Data) if windowSize < initialWindowSize =>
          val muxedFrame: MuxedFrame = muxDataFrame(maxFrameSize, initialWindowSize - windowSize, frame, accumulator)
          val windowSizeSum: Int = windowSize + muxedFrame.windowSize
          if (muxedFrame.windowSize < frame.payload.size) {
            assert(windowSizeSum == initialWindowSize)
            Muxed(windowSizeSum, frames, accumulator ++ muxedFrame.accumulator)
          } else {
            mux(windowSizeSum, frames.tail, accumulator ++ muxedFrame.accumulator)
          }
        case Some(d:Data) =>
          Muxed(windowSize, frames, accumulator)
      }
    }
    mux(0, frames, accumulator)
  }

  def muxDataFrame(
      maxFrameSize: Int,
      initialWindowSize: Int,
      frame: Http2Frame,
      accumulator: Vector[Http2Frame]): MuxedFrame = {

    @tailrec
    def mux(windowSize: Int, payload: ByteString, accumulator: Vector[Http2Frame]): MuxedFrame = {
      val frameSizeMin: Int = math.min(maxFrameSize, initialWindowSize - windowSize)
      if (payload.size > frameSizeMin) {
        val payloadChunk: ByteString = payload.take(frameSizeMin)
        mux(windowSize + frameSizeMin, payload.drop(frameSizeMin), accumulator :+ Data(frame.identifier, payloadChunk))
      } else {
        MuxedFrame(windowSize, accumulator :+ Data(frame.identifier, payload))
      }
    }
    mux(initialWindowSize, frame.payload, accumulator)
  }
}

