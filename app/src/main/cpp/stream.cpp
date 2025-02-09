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

namespace asio = boost::asio;
namespace sys = boost::system;

using asio::ip::tcp;
using asio::ip::udp;

using namespace std::literals;

namespace stream {

    struct session_t {
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

    control_server_t control_server;
  };
  
  void control_server_t::iterate(std::chrono::milliseconds timeout) {
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
    }

  void start() {
      broadcast_ctx_t ctx;
      start_broadcast(ctx);
  }
}

