/*
 * Copyright © 2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */
package com.typesafe.akka.http2

sealed abstract class Http2Error(val code:Int)

object Http2Error {
  case object NoError extends Http2Error(0x0)
  case object ProtocolError extends Http2Error(0x1)
  case object InternalError extends Http2Error(0x2)
  case object FlowControlError extends Http2Error(0x3)
  case object SettingsTimeout extends Http2Error(0x4)
  case object StreamClosed extends Http2Error(0x5)
  case object FrameSizeError extends Http2Error(0x6)
  case object RefusedStream extends Http2Error(0x7)
  case object Cancel extends Http2Error(0x8)
  case object CompressionError extends Http2Error(0x9)
  case object ConnectError extends Http2Error(0xa)
  case object EnhanceYourCalm extends Http2Error(0xb)
  case object InadequateSecurity extends Http2Error(0xc)
  case object Http_1_1_Required extends Http2Error(0xd)
}