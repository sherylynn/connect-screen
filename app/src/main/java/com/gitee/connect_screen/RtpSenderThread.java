package com.gitee.connect_screen;/*
AirPlay Mirror Protocol Packet Structure
======================================

Mirror Data Packet Header (128 bytes)
-----------------------------------

Bytes 0-3:   Payload size (32-bit integer)
Bytes 4-7:   Packet type and options
    - 4-5: Packet type identifier
        0x00 0x00: Encrypted packet containing non-IDR type 1 VCL NAL unit
        0x00 0x10: Encrypted packet containing IDR type 5 VCL NAL unit  
        0x01 0x00: Unencrypted packet containing type 7 SPS + type 8 PPS NAL units
        0x02 0x00: Unencrypted packet (old protocol) no payload, sent once per second
        0x05 0x00: Unencrypted packet with "streaming report", sent once per second
    - 6-7: Payload options
        0x00 0x00: Used for encrypted and "streaming report" packets
        0x1e 0x00: Used in old protocol (AirMyPC) no-payload packets
        0x16 0x01: Common in unencrypted h264 SPS+PPS packets
        0x56 0x01: Unencrypted h264 SPS+PPS packets (video stream stops, client sleeps)
        0x1e 0x01: Unencrypted h265/HEVC SPS+PPS packets
        0x5e 0x01: Unencrypted h265 SPS+PPS packets (video stream stops, client sleeps)

Bytes 8-15:  NTP timestamp (64-bit)
    - Not present in "streaming report" packets (type 0x05)

Bytes 16-127: Additional metadata (for SPS/PPS packets):
    16-19: Source width (float, value is x.0000 where x = unsigned short)
    20-23: Source height (float, value is x.0000 where x = unsigned short)
    24-39: Reserved (all 0x0)
    40-43: Source width repeated
    44-47: Source height repeated
    48-51: Other width value (unidentified)
    52-55: Other height value (unidentified)
    56-59: Display width
    60-63: Display height
    64-127: Reserved (all 0x0)

Payload
-------
For encrypted video packets (0x00):
- Contains encrypted H.264/H.265 NAL units
- Each NAL unit is prefixed with its size (4 bytes, big-endian)
- After decryption, size prefixes are replaced with 0x00000001 start codes

For SPS+PPS packets (0x01):
H.264:
- Contains unencrypted sequence and picture parameter sets
- Format details in payload bytes 0-11:
    0-5: Header
    6-7: SPS size (short, big-endian)
    8+: SPS data
    After SPS: PPS size (short, big-endian) followed by PPS data

H.265:
- Contains VPS, SPS and PPS units marked with:
    0xa0 0x00 0x01 0x00: VPS start
    0xa1 0x00 0x01 0x00: SPS start  
    0xa2 0x00 0x01 0x00: PPS start
- Each unit prefixed with 2-byte size

For streaming report packets (0x05):
- Contains binary property list with client performance data
- May include 25KB trailer with currently unidentified content
 */

public class RtpSenderThread {
    
}
