/**
 * @file src/stream.cpp
 * @brief Definitions for the streaming protocols.
 */

// standard includes
#include <fstream>
#include <future>
#include <queue>
#include <map>

// lib includes
#include <boost/endian/arithmetic.hpp>

#include "network.h"
#include "crypto.h"
#include "config.h"
#include "sync.h"
#include "logging.h"
#include "stream.h"
#include "thread_safe.h"
#include "globals.h"
#include "video.h"


#define IDX_START_A 0
#define IDX_START_B 1
#define IDX_INVALIDATE_REF_FRAMES 2
#define IDX_LOSS_STATS 3
#define IDX_INPUT_DATA 5
#define IDX_RUMBLE_DATA 6
#define IDX_TERMINATION 7
#define IDX_PERIODIC_PING 8
#define IDX_REQUEST_IDR_FRAME 9
#define IDX_ENCRYPTED 10
#define IDX_HDR_MODE 11
#define IDX_RUMBLE_TRIGGER_DATA 12
#define IDX_SET_MOTION_EVENT 13
#define IDX_SET_RGB_LED 14

static const short packetTypes[] = {
        0x0305,  // Start A
        0x0307,  // Start B
        0x0301,  // Invalidate reference frames
        0x0201,  // Loss Stats
        0x0204,  // Frame Stats (unused)
        0x0206,  // Input data
        0x010b,  // Rumble data
        0x0109,  // Termination
        0x0200,  // Periodic Ping
        0x0302,  // IDR frame
        0x0001,  // fully encrypted
        0x010e,  // HDR mode
        0x5500,  // Rumble triggers (Sunshine protocol extension)
        0x5501,  // Set motion event (Sunshine protocol extension)
        0x5502,  // Set RGB LED (Sunshine protocol extension)
};

namespace asio = boost::asio;
namespace sys = boost::system;

using asio::ip::tcp;
using asio::ip::udp;

using namespace std::literals;

namespace stream {

    enum class socket_e : int {
        video,  ///< Video
        audio  ///< Audio
    };
    using av_session_id_t = std::variant<asio::ip::address, std::string>;  // IP address or SS-Ping-Payload from RTSP handshake
    using message_queue_t = std::shared_ptr<safe::queue_t<std::pair<udp::endpoint, std::string>>>;
    using message_queue_queue_t = std::shared_ptr<safe::queue_t<std::tuple<socket_e, av_session_id_t, message_queue_t>>>;

    struct session_t {
        config_t config;
        safe::mail_t mail;


        std::thread audioThread;
        std::thread videoThread;

        std::chrono::steady_clock::time_point pingTimeout;

        boost::asio::ip::address localAddress;

        struct {
            std::string ping_payload;

            int lowseq;
            udp::endpoint peer;

            std::uint64_t gcm_iv_counter;

            safe::mail_raw_t::event_t<bool> idr_events;
            safe::mail_raw_t::event_t<std::pair<int64_t, int64_t>> invalidate_ref_frames_events;

        } video;
    };

  class control_server_t {
  public:
    int bind(net::af_e address_family, std::uint16_t port) {
      _host = net::host_create(address_family, _addr, port);

      return !(bool) _host;
    }

    // Get session associated with address.
    // If none are found, try to find a session not yet claimed. (It will be marked by a port of value 0
    // If none of those are found, return nullptr
    session_t *get_session(const net::peer_t peer, uint32_t connect_data);

    // Circular dependency:
    //   iterate refers to session
    //   session refers to broadcast_ctx_t
    //   broadcast_ctx_t refers to control_server_t
    // Therefore, iterate is implemented further down the source file
    void iterate(std::chrono::milliseconds timeout);

    /**
     * @brief Call the handler for a given control stream message.
     * @param type The message type.
     * @param session The session the message was received on.
     * @param payload The payload of the message.
     * @param reinjected `true` if this message is being reprocessed after decryption.
     */
    void call(std::uint16_t type, session_t *session, const std::string_view &payload, bool reinjected);

    void map(uint16_t type, std::function<void(session_t *, const std::string_view &)> cb) {
      _map_type_cb.emplace(type, std::move(cb));
    }

    int send(const std::string_view &payload, net::peer_t peer) {
      auto packet = enet_packet_create(payload.data(), payload.size(), ENET_PACKET_FLAG_RELIABLE);
      if (enet_peer_send(peer, 0, packet)) {
        enet_packet_destroy(packet);

        return -1;
      }

      return 0;
    }

    void flush() {
      enet_host_flush(_host.get());
    }

    // Callbacks
    std::unordered_map<std::uint16_t, std::function<void(session_t *, const std::string_view &)>> _map_type_cb;

    // All active sessions (including those still waiting for a peer to connect)
    sync_util::sync_t<std::vector<std::shared_ptr<session_t>>> _sessions;

    // ENet peer to session mapping for sessions with a peer connected
    sync_util::sync_t<std::map<net::peer_t, session_t *>> _peer_to_session;

    ENetAddress _addr;
    net::host_t _host;
  };

    struct broadcast_ctx_t {
        message_queue_queue_t message_queue_queue;

        std::thread recv_thread;
        std::thread video_thread;
        std::thread audio_thread;
        std::thread control_thread;

        asio::io_context io_context;

        udp::socket video_sock {io_context};
        udp::socket audio_sock {io_context};
        control_server_t control_server;
  };

    int start_broadcast(broadcast_ctx_t &ctx);
    void end_broadcast(broadcast_ctx_t &ctx);

    static auto broadcast = safe::make_shared<broadcast_ctx_t>(start_broadcast, end_broadcast);


    session_t *control_server_t::get_session(const net::peer_t peer, uint32_t connect_data) {
        return nullptr;
//        {
//            // Fast path - look up existing session by peer
//            auto lg = _peer_to_session.lock();
//            auto it = _peer_to_session->find(peer);
//            if (it != _peer_to_session->end()) {
//                return it->second;
//            }
//        }
//
//        // Slow path - process new session
//        TUPLE_2D(peer_port, peer_addr, platf::from_sockaddr_ex((sockaddr *) &peer->address.address));
//        auto lg = _sessions.lock();
//        for (auto pos = std::begin(*_sessions); pos != std::end(*_sessions); ++pos) {
//            auto session_p = *pos;
//
//            // Skip sessions that are already established
//            if (session_p->control.peer) {
//                continue;
//            }
//
//            // Identify the connection by the unique connect data if the client supports it.
//            // Only fall back to IP address matching for clients without session ID support.
//            if (session_p->config.mlFeatureFlags & ML_FF_SESSION_ID_V1) {
//                if (session_p->control.connect_data != connect_data) {
//                    continue;
//                } else {
//                    BOOST_LOG(debug) << "Initialized new control stream session by connect data match [v2]"sv;
//                }
//            } else {
//                if (session_p->control.expected_peer_address != peer_addr) {
//                    continue;
//                } else {
//                    BOOST_LOG(debug) << "Initialized new control stream session by IP address match [v1]"sv;
//                }
//            }
//
//            // Once the control stream connection is established, RTSP session state can be torn down
//            rtsp_stream::launch_session_clear(session_p->launch_session_id);
//
//            session_p->control.peer = peer;
//
//            // Use the local address from the control connection as the source address
//            // for other communications to the client. This is necessary to ensure
//            // proper routing on multi-homed hosts.
//            auto local_address = platf::from_sockaddr((sockaddr *) &peer->localAddress.address);
//            session_p->localAddress = boost::asio::ip::make_address(local_address);
//
//            BOOST_LOG(debug) << "Control local address ["sv << local_address << ']';
//            BOOST_LOG(debug) << "Control peer address ["sv << peer_addr << ':' << peer_port << ']';
//
//            // Insert this into the map for O(1) lookups in the future
//            auto ptslg = _peer_to_session.lock();
//            _peer_to_session->emplace(peer, session_p);
//            return session_p;
//        }
//
//        return nullptr;
    }

    std::string from_sockaddr(const sockaddr *const ip_addr) {
        char data[INET6_ADDRSTRLEN] = {};

        auto family = ip_addr->sa_family;
        if (family == AF_INET6) {
            inet_ntop(AF_INET6, &((sockaddr_in6 *) ip_addr)->sin6_addr, data, INET6_ADDRSTRLEN);
        } else if (family == AF_INET) {
            inet_ntop(AF_INET, &((sockaddr_in *) ip_addr)->sin_addr, data, INET_ADDRSTRLEN);
        }

        return std::string {data};
    }
  
  void control_server_t::iterate(std::chrono::milliseconds timeout) {
      ENetEvent event;
      auto res = enet_host_service(_host.get(), &event, timeout.count());
      if (res > 0) {
          auto session = get_session(event.peer, event.data);
          if (!session) {
              BOOST_LOG(warning) << "Rejected connection from ["sv << from_sockaddr((sockaddr *) &event.peer->address.address) << "]: it's not properly set up"sv;
              enet_peer_disconnect_now(event.peer, 0);

              return;
          }
          session->pingTimeout = std::chrono::steady_clock::now() + config::stream.ping_timeout;

      }
  }


  void controlBroadcastThread(control_server_t *server) {
      server->map(packetTypes[IDX_PERIODIC_PING], [](session_t *session, const std::string_view &payload) {
          BOOST_LOG(verbose) << "type [IDX_PERIODIC_PING]"sv;
      });

      server->map(packetTypes[IDX_START_A], [&](session_t *session, const std::string_view &payload) {
          BOOST_LOG(debug) << "type [IDX_START_A]"sv;
      });

      server->map(packetTypes[IDX_START_B], [&](session_t *session, const std::string_view &payload) {
          BOOST_LOG(debug) << "type [IDX_START_B]"sv;
      });


      server->map(packetTypes[IDX_LOSS_STATS], [&](session_t *session, const std::string_view &payload) {
          int32_t *stats = (int32_t *) payload.data();
          auto count = stats[0];
          std::chrono::milliseconds t {stats[1]};

          auto lastGoodFrame = stats[3];

          BOOST_LOG(verbose)
              << "type [IDX_LOSS_STATS]"sv << std::endl
              << "---begin stats---" << std::endl
              << "loss count since last report [" << count << ']' << std::endl
              << "time in milli since last report [" << t.count() << ']' << std::endl
              << "last good frame [" << lastGoodFrame << ']' << std::endl
              << "---end stats---";
      });


      server->map(packetTypes[IDX_ENCRYPTED], [server](session_t *session, const std::string_view &payload) {
          BOOST_LOG(verbose) << "type [IDX_ENCRYPTED]"sv;
      });
      // Check for both the full shutdown event and the shutdown event for this
      // broadcast to ensure we can inform connected clients of our graceful
      // termination when we shut down.
      auto shutdown_event = mail::man->event<bool>(mail::shutdown);
      auto broadcast_shutdown_event = mail::man->event<bool>(mail::broadcast_shutdown);
      while (!shutdown_event->peek() && !broadcast_shutdown_event->peek()) {
          server->iterate(150ms);
      }
  }

    std::vector<uint8_t> replace(const std::string_view &original, const std::string_view &old, const std::string_view &_new) {
        std::vector<uint8_t> replaced;
        replaced.reserve(original.size() + _new.size() - old.size());

        auto begin = std::begin(original);
        auto end = std::end(original);
        auto next = std::search(begin, end, std::begin(old), std::end(old));

        std::copy(begin, next, std::back_inserter(replaced));
        if (next != end) {
            std::copy(std::begin(_new), std::end(_new), std::back_inserter(replaced));
            std::copy(next + old.size(), end, std::back_inserter(replaced));
        }

        return replaced;
    }

    void videoBroadcastThread(udp::socket &sock) {
        auto shutdown_event = mail::man->event<bool>(mail::broadcast_shutdown);
        auto packets = mail::man->queue<video::packet_t>(mail::video_packets);
        auto timebase = boost::posix_time::microsec_clock::universal_time();

        logging::min_max_avg_periodic_logger<double> frame_processing_latency_logger(debug, "Frame processing latency", "ms");

        logging::time_delta_periodic_logger frame_send_batch_latency_logger(debug, "Network: each send_batch() latency");
        logging::time_delta_periodic_logger frame_fec_latency_logger(debug, "Network: each FEC block latency");
        logging::time_delta_periodic_logger frame_network_latency_logger(debug, "Network: frame's overall network latency");

        auto ratecontrol_next_frame_start = std::chrono::steady_clock::now();

        while (auto packet = packets->pop()) {
            if (shutdown_event->peek()) {
                break;
            }

            frame_network_latency_logger.first_point_now();

            auto session = (session_t *) packet->channel_data;
            auto lowseq = session->video.lowseq;

            std::string_view payload {(char *) packet->data(), packet->data_size()};
            std::vector<uint8_t> payload_with_replacements;


            // Apply replacements on the packet payload before performing any other operations.
            // We need to know the final frame size to calculate the last packet size, and we
            // must avoid matching replacements against the frame header or any other non-video
            // part of the payload.
            if (packet->is_idr() && packet->replacements) {
                for (auto &replacement : *packet->replacements) {
                    auto frame_old = replacement.old;
                    auto frame_new = replacement._new;

                    payload_with_replacements = replace(payload, frame_old, frame_new);
                    payload = {(char *) payload_with_replacements.data(), payload_with_replacements.size()};
                }
            }
        }
    }


    void end_broadcast(broadcast_ctx_t &ctx) {
        auto broadcast_shutdown_event = mail::man->event<bool>(mail::broadcast_shutdown);

        broadcast_shutdown_event->raise(true);

        auto video_packets = mail::man->queue<video::packet_t>(mail::video_packets);
//        auto audio_packets = mail::man->queue<audio::packet_t>(mail::audio_packets);

        // Minimize delay stopping video/audio threads
        video_packets->stop();
//        audio_packets->stop();

        ctx.message_queue_queue->stop();
        ctx.io_context.stop();

        ctx.video_sock.close();
        ctx.audio_sock.close();

        video_packets.reset();
//        audio_packets.reset();

        BOOST_LOG(debug) << "Waiting for main listening thread to end..."sv;
        ctx.recv_thread.join();
        BOOST_LOG(debug) << "Waiting for main video thread to end..."sv;
        ctx.video_thread.join();
        BOOST_LOG(debug) << "Waiting for main audio thread to end..."sv;
        ctx.audio_thread.join();
        BOOST_LOG(debug) << "Waiting for main control thread to end..."sv;
        ctx.control_thread.join();
        BOOST_LOG(debug) << "All broadcasting threads ended"sv;

        broadcast_shutdown_event->reset();
    }

    int start_broadcast(broadcast_ctx_t &ctx) {
        auto address_family = net::af_from_enum_string(config::sunshine.address_family);
        auto protocol = address_family == net::IPV4 ? udp::v4() : udp::v6();
        auto control_port = net::map_port(CONTROL_PORT);
        auto video_port = net::map_port(VIDEO_STREAM_PORT);
        auto audio_port = net::map_port(AUDIO_STREAM_PORT);

        if (ctx.control_server.bind(address_family, control_port)) {
            BOOST_LOG(error) << "Couldn't bind Control server to port ["sv << control_port
                             << "], likely another process already bound to the port"sv;

            return -1;
        }
        BOOST_LOG(info) << "bind Control server to port "sv << control_port;
        
        boost::system::error_code ec;
        ctx.video_sock.open(protocol, ec);
        if (ec) {
          BOOST_LOG(fatal) << "Couldn't open socket for Video server: "sv << ec.message();

          return -1;
        }

        // Set video socket send buffer size (SO_SENDBUF) to 1MB
        try {
            ctx.video_sock.set_option(boost::asio::socket_base::send_buffer_size(1024 * 1024));
        } catch (...) {
            BOOST_LOG(error) << "Failed to set video socket send buffer size (SO_SENDBUF)";
        }

        ctx.video_sock.bind(udp::endpoint(protocol, video_port), ec);
        if (ec) {
            BOOST_LOG(fatal) << "Couldn't bind Video server to port ["sv << video_port << "]: "sv << ec.message();

            return -1;
        }
        ctx.video_thread = std::thread {videoBroadcastThread, std::ref(ctx.video_sock)};
        ctx.control_thread = std::thread {controlBroadcastThread, &ctx.control_server};
        return 0;
    }


    int recv_ping(session_t *session, decltype(broadcast)::ptr_t ref, socket_e type, std::string_view expected_payload, udp::endpoint &peer, std::chrono::milliseconds timeout) {
        auto messages = std::make_shared<message_queue_t::element_type>(30);
        av_session_id_t session_id = std::string {expected_payload};

        // Only allow matches on the peer address for legacy clients
//        if (!(session->config.mlFeatureFlags & ML_FF_SESSION_ID_V1)) {
//            ref->message_queue_queue->raise(type, peer.address(), messages);
//        }
        ref->message_queue_queue->raise(type, session_id, messages);

        auto fg = util::fail_guard([&]() {
            messages->stop();

            // remove message queue from session
//            if (!(session->config.mlFeatureFlags & ML_FF_SESSION_ID_V1)) {
//                ref->message_queue_queue->raise(type, peer.address(), nullptr);
//            }
            ref->message_queue_queue->raise(type, session_id, nullptr);
        });

        auto start_time = std::chrono::steady_clock::now();
        auto current_time = start_time;

        while (current_time - start_time < config::stream.ping_timeout) {
            auto delta_time = current_time - start_time;

            auto msg_opt = messages->pop(config::stream.ping_timeout - delta_time);
            if (!msg_opt) {
                break;
            }

            TUPLE_2D_REF(recv_peer, msg, *msg_opt);
            if (msg.find(expected_payload) != std::string::npos) {
                // Match the new PING payload format
                BOOST_LOG(debug) << "Received ping [v2] from "sv << recv_peer.address() << ':' << recv_peer.port() << " ["sv << util::hex_vec(msg) << ']';
//            } else if (!(session->config.mlFeatureFlags & ML_FF_SESSION_ID_V1) && msg == "PING"sv) {
//                // Match the legacy fixed PING payload only if the new type is not supported
//                BOOST_LOG(debug) << "Received ping [v1] from "sv << recv_peer.address() << ':' << recv_peer.port() << " ["sv << util::hex_vec(msg) << ']';
            } else {
                BOOST_LOG(debug) << "Received non-ping from "sv << recv_peer.address() << ':' << recv_peer.port() << " ["sv << util::hex_vec(msg) << ']';
                current_time = std::chrono::steady_clock::now();
                continue;
            }

            // Update connection details.
            peer = recv_peer;
            return 0;
        }

        BOOST_LOG(error) << "Initial Ping Timeout"sv;
        return -1;
    }

  void start() {
      config_t config;
      session::launch_session_t session;
      auto stream_session = session::alloc(config, session);
      session::start(*stream_session, "1.2.3.4");
      broadcast_ctx_t* ctxPtr = new broadcast_ctx_t();
      ctxPtr->control_server._sessions->push_back(stream_session);
      start_broadcast(*ctxPtr);
  }

  namespace session {
      int start(session_t &session, const std::string &addr_string) {
//          session.input = input::alloc(session.mail);

//          session.broadcast_ref = broadcast.ref();
//          if (!session.broadcast_ref) {
//              return -1;
//          }

//          session.control.expected_peer_address = addr_string;
          BOOST_LOG(debug) << "Expecting incoming session connections from "sv << addr_string;

          // Insert this session into the session list
//          {
//              auto lg = session.broadcast_ref->control_server._sessions.lock();
//              session.broadcast_ref->control_server._sessions->push_back(&session);
//          }

          auto addr = boost::asio::ip::make_address(addr_string);
          session.video.peer.address(addr);
          session.video.peer.port(0);

//          session.audio.peer.address(addr);
//          session.audio.peer.port(0);

          session.pingTimeout = std::chrono::steady_clock::now() + config::stream.ping_timeout;

//          session.audioThread = std::thread {audioThread, &session};
//          session.videoThread = std::thread {videoThread, &session};

//          session.state.store(state_e::RUNNING, std::memory_order_relaxed);

          // If this is the first session, invoke the platform callbacks
//          if (++running_sessions == 1) {
//              platf::streaming_will_start();
//#if defined SUNSHINE_TRAY && SUNSHINE_TRAY >= 1
//              system_tray::update_tray_playing(proc::proc.get_last_run_app_name());
//#endif
//          }

          return 0;
      }




      std::shared_ptr<session_t> alloc(config_t &config, launch_session_t &launch_session) {
          auto session = std::make_shared<session_t>();

          auto mail = std::make_shared<safe::mail_raw_t>();

//        session->shutdown_event = mail->event<bool>(mail::shutdown);
//        session->launch_session_id = launch_session.id;
//
//        session->config = config;
//
//        session->control.connect_data = launch_session.control_connect_data;
//        session->control.feedback_queue = mail->queue<platf::gamepad_feedback_msg_t>(mail::gamepad_feedback);
//        session->control.hdr_queue = mail->event<video::hdr_info_t>(mail::hdr);
//        session->control.legacy_input_enc_iv = launch_session.iv;
//        session->control.cipher = crypto::cipher::gcm_t {
//                launch_session.gcm_key,
//                false
//        };
//
//        session->video.idr_events = mail->event<bool>(mail::idr);
//        session->video.invalidate_ref_frames_events = mail->event<std::pair<int64_t, int64_t>>(mail::invalidate_ref_frames);
//        session->video.lowseq = 0;
//        session->video.ping_payload = launch_session.av_ping_payload;
//        if (config.encryptionFlagsEnabled & SS_ENC_VIDEO) {
//            BOOST_LOG(info) << "Video encryption enabled"sv;
//            session->video.cipher = crypto::cipher::gcm_t {
//                    launch_session.gcm_key,
//                    false
//            };
//            session->video.gcm_iv_counter = 0;
//        }
//
//        constexpr auto max_block_size = crypto::cipher::round_to_pkcs7_padded(2048);
//
//        util::buffer_t<char> shards {RTPA_TOTAL_SHARDS * max_block_size};
//        util::buffer_t<uint8_t *> shards_p {RTPA_TOTAL_SHARDS};
//
//        for (auto x = 0; x < RTPA_TOTAL_SHARDS; ++x) {
//            shards_p[x] = (uint8_t *) &shards[x * max_block_size];
//        }
//
//        // Audio FEC spans multiple audio packets,
//        // therefore its session specific
//        session->audio.shards = std::move(shards);
//        session->audio.shards_p = std::move(shards_p);
//
//        session->audio.fec_packet.rtp.header = 0x80;
//        session->audio.fec_packet.rtp.packetType = 127;
//        session->audio.fec_packet.rtp.timestamp = 0;
//        session->audio.fec_packet.rtp.ssrc = 0;
//
//        session->audio.fec_packet.fecHeader.payloadType = 97;
//        session->audio.fec_packet.fecHeader.ssrc = 0;
//
//        session->audio.cipher = crypto::cipher::cbc_t {
//                launch_session.gcm_key,
//                true
//        };
//
//        session->audio.ping_payload = launch_session.av_ping_payload;
//        session->audio.avRiKeyId = util::endian::big(*(std::uint32_t *) launch_session.iv.data());
//        session->audio.sequenceNumber = 0;
//        session->audio.timestamp = 0;
//
//        session->control.peer = nullptr;
//        session->state.store(state_e::STOPPED, std::memory_order_relaxed);

          session->mail = std::move(mail);

          return session;
      }
    }
}

