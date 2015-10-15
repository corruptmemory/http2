package com.typesafe.akka.http2

import akka.util.ByteString

/*
 +-----------------------------------------------+
 |                 Length (24)                   |
 +---------------+---------------+---------------+
 |   Type (8)    |   Flags (8)   |
 +-+-------------+---------------+-------------------------------+
 |R|                 Stream Identifier (31)                      |
 +=+=============================================================+
 |                   Frame Payload (0...)                      ...
 */

final case class Http2Frame(length: Int,
                            frameType: Byte,
                            flags: Byte,
                            identifier: Int,
                            payload: ByteString)