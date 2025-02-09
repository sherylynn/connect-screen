/**
 * @file src/stream.h
 * @brief Declarations for the streaming protocols.
 */
#pragma once

namespace stream {
    constexpr auto VIDEO_STREAM_PORT = 9;
    constexpr auto CONTROL_PORT = 10;
    constexpr auto AUDIO_STREAM_PORT = 11;
    void start();
}