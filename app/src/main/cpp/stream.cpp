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
#include "config.h"
#include "sync.h"
#include "logging.h"
#include "stream.h"
#include "thread_safe.h"
#include "globals.h"
#include "video.h"

namespace asio = boost::asio;
namespace sys = boost::system;

using asio::ip::tcp;
using asio::ip::udp;

using namespace std::literals;

namespace stream {

    struct session_t {

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
    sync_util::sync_t<std::vector<session_t *>> _sessions;

    // ENet peer to session mapping for sessions with a peer connected
    sync_util::sync_t<std::map<net::peer_t, session_t *>> _peer_to_session;

    ENetAddress _addr;
    net::host_t _host;
  };

    struct broadcast_ctx_t {

    std::thread control_thread;
    std::thread video_thread;

    control_server_t control_server;
    asio::io_context io_context;

    udp::socket video_sock {io_context};
  };
  
  void control_server_t::iterate(std::chrono::milliseconds timeout) {
      ENetEvent event;
      auto res = enet_host_service(_host.get(), &event, timeout.count());
      if (res > 0) {
          BOOST_LOG(info) << "enet event result: " << res;
      }
  }


  void controlBroadcastThread(control_server_t *server) {
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

  void start() {
      broadcast_ctx_t* ctxPtr = new broadcast_ctx_t();
      start_broadcast(*ctxPtr);
  }
}

