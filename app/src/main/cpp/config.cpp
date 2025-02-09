/**
 * @file src/config.cpp
 * @brief Definitions for the configuration of Sunshine.
 */
// standard includes
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <functional>
#include <iostream>
#include <thread>
#include <unordered_map>
#include <utility>

// lib includes
#include <boost/asio.hpp>
#include <boost/filesystem.hpp>
#include <boost/property_tree/json_parser.hpp>
#include <boost/property_tree/ptree.hpp>

// local includes
#include "config.h"
#include "logging.h"
#include "utility.h"

namespace fs = std::filesystem;
using namespace std::literals;

#define CA_DIR "credentials"
#define PRIVATE_KEY_FILE CA_DIR "/cakey.pem"
#define CERTIFICATE_FILE CA_DIR "/cacert.pem"

namespace platf {
    inline std::filesystem::path appdata() {
        return std::filesystem::path {"/"};
    }
}

#define APPS_JSON_PATH platf::appdata().string() + "/apps.json"

namespace config {
    sunshine_t sunshine {
            "en",  // locale
            2,  // min_log_level
            0,  // flags
            {},  // User file
            {},  // Username
            {},  // Password
            {},  // Password Salt
            platf::appdata().string() + "/sunshine.conf",  // config file
            {},  // cmd args
            47989,  // Base port number
            "ipv4",  // Address family
            platf::appdata().string() + "/sunshine.log",  // log file
            false,  // notify_pre_releases
            {},  // prep commands
    };

    stream_t stream {
            10s,  // ping_timeout

            APPS_JSON_PATH,

            20,  // fecPercentage

            ENCRYPTION_MODE_NEVER,  // lan_encryption_mode
            ENCRYPTION_MODE_OPPORTUNISTIC,  // wan_encryption_mode
    };
}  // namespace config
