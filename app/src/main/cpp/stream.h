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


    struct session_t;

    struct config_t {

        int packetsize;
        int minRequiredFecPackets;
        int mlFeatureFlags;
        int controlProtocolType;
        int audioQosType;
        int videoQosType;

        uint32_t encryptionFlagsEnabled;

        std::optional<int> gcmap;
    };


    namespace session {
        enum class state_e : int {
            STOPPED,  ///< The session is stopped
            STOPPING,  ///< The session is stopping
            STARTING,  ///< The session is starting
            RUNNING,  ///< The session is running
        };


        struct launch_session_t {
            uint32_t id;


            std::string av_ping_payload;
            uint32_t control_connect_data;

            bool host_audio;
            std::string unique_id;
            int width;
            int height;
            int fps;
            int gcmap;
            int appid;
            int surround_info;
            std::string surround_params;
            bool enable_hdr;
            bool enable_sops;

            std::string rtsp_url_scheme;
            uint32_t rtsp_iv_counter;
        };

        std::shared_ptr<session_t> alloc(config_t &config, launch_session_t &launch_session);
        int start(session_t &session, const std::string &addr_string);
        void stop(session_t &session);
        void join(session_t &session);
        state_e state(session_t &session);
    }  // namespace session
}