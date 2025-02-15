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
//#include "entry_handler.h"
//#include "file_handler.h"
#include "logging.h"
//#include "nvhttp.h"
#include "platform/common.h"
//#include "rtsp.h"
#include "utility.h"

#ifdef _WIN32
#include <shellapi.h>
#endif

//#ifndef __APPLE__
// For NVENC legacy constants
//#include <ffnvcodec/nvEncodeAPI.h>
//#endif

namespace fs = std::filesystem;
using namespace std::literals;

#define CA_DIR "credentials"
#define PRIVATE_KEY_FILE CA_DIR "/cakey.pem"
#define CERTIFICATE_FILE CA_DIR "/cacert.pem"

#define APPS_JSON_PATH platf::appdata().string() + "/apps.json"

namespace config {

//    namespace nv {
//
//        nvenc::nvenc_two_pass twopass_from_view(const std::string_view &preset) {
//            if (preset == "disabled") {
//                return nvenc::nvenc_two_pass::disabled;
//            }
//            if (preset == "quarter_res") {
//                return nvenc::nvenc_two_pass::quarter_resolution;
//            }
//            if (preset == "full_res") {
//                return nvenc::nvenc_two_pass::full_resolution;
//            }
//            BOOST_LOG(warning) << "config: unknown nvenc_twopass value: " << preset;
//            return nvenc::nvenc_two_pass::quarter_resolution;
//        }
//
//    }  // namespace nv

    namespace amd {
#if !defined(_WIN32) || defined(DOXYGEN)
        // values accurate as of 27/12/2022, but aren't strictly necessary for MacOS build
#define AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_SPEED 100
#define AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_QUALITY 30
#define AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_BALANCED 70
#define AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_SPEED 10
#define AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_QUALITY 0
#define AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_BALANCED 5
#define AMF_VIDEO_ENCODER_QUALITY_PRESET_SPEED 1
#define AMF_VIDEO_ENCODER_QUALITY_PRESET_QUALITY 2
#define AMF_VIDEO_ENCODER_QUALITY_PRESET_BALANCED 0
#define AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_CONSTANT_QP 0
#define AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_CBR 3
#define AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR 2
#define AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR 1
#define AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_CONSTANT_QP 0
#define AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_CBR 3
#define AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR 2
#define AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR 1
#define AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_CONSTANT_QP 0
#define AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_CBR 1
#define AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR 2
#define AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR 3
#define AMF_VIDEO_ENCODER_AV1_USAGE_TRANSCODING 0
#define AMF_VIDEO_ENCODER_AV1_USAGE_LOW_LATENCY 1
#define AMF_VIDEO_ENCODER_AV1_USAGE_ULTRA_LOW_LATENCY 2
#define AMF_VIDEO_ENCODER_AV1_USAGE_WEBCAM 3
#define AMF_VIDEO_ENCODER_AV1_USAGE_LOW_LATENCY_HIGH_QUALITY 5
#define AMF_VIDEO_ENCODER_HEVC_USAGE_TRANSCODING 0
#define AMF_VIDEO_ENCODER_HEVC_USAGE_ULTRA_LOW_LATENCY 1
#define AMF_VIDEO_ENCODER_HEVC_USAGE_LOW_LATENCY 2
#define AMF_VIDEO_ENCODER_HEVC_USAGE_WEBCAM 3
#define AMF_VIDEO_ENCODER_HEVC_USAGE_LOW_LATENCY_HIGH_QUALITY 5
#define AMF_VIDEO_ENCODER_USAGE_TRANSCODING 0
#define AMF_VIDEO_ENCODER_USAGE_ULTRA_LOW_LATENCY 1
#define AMF_VIDEO_ENCODER_USAGE_LOW_LATENCY 2
#define AMF_VIDEO_ENCODER_USAGE_WEBCAM 3
#define AMF_VIDEO_ENCODER_USAGE_LOW_LATENCY_HIGH_QUALITY 5
#define AMF_VIDEO_ENCODER_UNDEFINED 0
#define AMF_VIDEO_ENCODER_CABAC 1
#define AMF_VIDEO_ENCODER_CALV 2
#else
        #ifdef _GLIBCXX_USE_C99_INTTYPES
    #undef _GLIBCXX_USE_C99_INTTYPES
  #endif
  #include <AMF/components/VideoEncoderAV1.h>
  #include <AMF/components/VideoEncoderHEVC.h>
  #include <AMF/components/VideoEncoderVCE.h>
#endif

        enum class quality_av1_e : int {
            speed = AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_SPEED,  ///< Speed preset
            quality = AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_QUALITY,  ///< Quality preset
            balanced = AMF_VIDEO_ENCODER_AV1_QUALITY_PRESET_BALANCED  ///< Balanced preset
        };

        enum class quality_hevc_e : int {
            speed = AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_SPEED,  ///< Speed preset
            quality = AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_QUALITY,  ///< Quality preset
            balanced = AMF_VIDEO_ENCODER_HEVC_QUALITY_PRESET_BALANCED  ///< Balanced preset
        };

        enum class quality_h264_e : int {
            speed = AMF_VIDEO_ENCODER_QUALITY_PRESET_SPEED,  ///< Speed preset
            quality = AMF_VIDEO_ENCODER_QUALITY_PRESET_QUALITY,  ///< Quality preset
            balanced = AMF_VIDEO_ENCODER_QUALITY_PRESET_BALANCED  ///< Balanced preset
        };

        enum class rc_av1_e : int {
            cbr = AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_CBR,  ///< CBR
            cqp = AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_CONSTANT_QP,  ///< CQP
            vbr_latency = AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR,  ///< VBR with latency constraints
            vbr_peak = AMF_VIDEO_ENCODER_AV1_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR  ///< VBR with peak constraints
        };

        enum class rc_hevc_e : int {
            cbr = AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_CBR,  ///< CBR
            cqp = AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_CONSTANT_QP,  ///< CQP
            vbr_latency = AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR,  ///< VBR with latency constraints
            vbr_peak = AMF_VIDEO_ENCODER_HEVC_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR  ///< VBR with peak constraints
        };

        enum class rc_h264_e : int {
            cbr = AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_CBR,  ///< CBR
            cqp = AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_CONSTANT_QP,  ///< CQP
            vbr_latency = AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_LATENCY_CONSTRAINED_VBR,  ///< VBR with latency constraints
            vbr_peak = AMF_VIDEO_ENCODER_RATE_CONTROL_METHOD_PEAK_CONSTRAINED_VBR  ///< VBR with peak constraints
        };

        enum class usage_av1_e : int {
            transcoding = AMF_VIDEO_ENCODER_AV1_USAGE_TRANSCODING,  ///< Transcoding preset
            webcam = AMF_VIDEO_ENCODER_AV1_USAGE_WEBCAM,  ///< Webcam preset
            lowlatency_high_quality = AMF_VIDEO_ENCODER_AV1_USAGE_LOW_LATENCY_HIGH_QUALITY,  ///< Low latency high quality preset
            lowlatency = AMF_VIDEO_ENCODER_AV1_USAGE_LOW_LATENCY,  ///< Low latency preset
            ultralowlatency = AMF_VIDEO_ENCODER_AV1_USAGE_ULTRA_LOW_LATENCY  ///< Ultra low latency preset
        };

        enum class usage_hevc_e : int {
            transcoding = AMF_VIDEO_ENCODER_HEVC_USAGE_TRANSCODING,  ///< Transcoding preset
            webcam = AMF_VIDEO_ENCODER_HEVC_USAGE_WEBCAM,  ///< Webcam preset
            lowlatency_high_quality = AMF_VIDEO_ENCODER_HEVC_USAGE_LOW_LATENCY_HIGH_QUALITY,  ///< Low latency high quality preset
            lowlatency = AMF_VIDEO_ENCODER_HEVC_USAGE_LOW_LATENCY,  ///< Low latency preset
            ultralowlatency = AMF_VIDEO_ENCODER_HEVC_USAGE_ULTRA_LOW_LATENCY  ///< Ultra low latency preset
        };

        enum class usage_h264_e : int {
            transcoding = AMF_VIDEO_ENCODER_USAGE_TRANSCODING,  ///< Transcoding preset
            webcam = AMF_VIDEO_ENCODER_USAGE_WEBCAM,  ///< Webcam preset
            lowlatency_high_quality = AMF_VIDEO_ENCODER_USAGE_LOW_LATENCY_HIGH_QUALITY,  ///< Low latency high quality preset
            lowlatency = AMF_VIDEO_ENCODER_USAGE_LOW_LATENCY,  ///< Low latency preset
            ultralowlatency = AMF_VIDEO_ENCODER_USAGE_ULTRA_LOW_LATENCY  ///< Ultra low latency preset
        };

        enum coder_e : int {
            _auto = AMF_VIDEO_ENCODER_UNDEFINED,  ///< Auto
            cabac = AMF_VIDEO_ENCODER_CABAC,  ///< CABAC
            cavlc = AMF_VIDEO_ENCODER_CALV  ///< CAVLC
        };

        template<class T>
        std::optional<int> quality_from_view(const std::string_view &quality_type, const std::optional<int>(&original)) {
#define _CONVERT_(x) \
  if (quality_type == #x##sv) \
  return (int) T::x
            _CONVERT_(balanced);
            _CONVERT_(quality);
            _CONVERT_(speed);
#undef _CONVERT_
            return original;
        }

        template<class T>
        std::optional<int> rc_from_view(const std::string_view &rc, const std::optional<int>(&original)) {
#define _CONVERT_(x) \
  if (rc == #x##sv) \
  return (int) T::x
            _CONVERT_(cbr);
            _CONVERT_(cqp);
            _CONVERT_(vbr_latency);
            _CONVERT_(vbr_peak);
#undef _CONVERT_
            return original;
        }

        template<class T>
        std::optional<int> usage_from_view(const std::string_view &usage, const std::optional<int>(&original)) {
#define _CONVERT_(x) \
  if (usage == #x##sv) \
  return (int) T::x
            _CONVERT_(lowlatency);
            _CONVERT_(lowlatency_high_quality);
            _CONVERT_(transcoding);
            _CONVERT_(ultralowlatency);
            _CONVERT_(webcam);
#undef _CONVERT_
            return original;
        }

        int coder_from_view(const std::string_view &coder) {
            if (coder == "auto"sv) {
                return _auto;
            }
            if (coder == "cabac"sv || coder == "ac"sv) {
                return cabac;
            }
            if (coder == "cavlc"sv || coder == "vlc"sv) {
                return cavlc;
            }

            return _auto;
        }
    }  // namespace amd

    namespace qsv {
        enum preset_e : int {
            veryslow = 1,  ///< veryslow preset
            slower = 2,  ///< slower preset
            slow = 3,  ///< slow preset
            medium = 4,  ///< medium preset
            fast = 5,  ///< fast preset
            faster = 6,  ///< faster preset
            veryfast = 7  ///< veryfast preset
        };

        enum cavlc_e : int {
            _auto = false,  ///< Auto
            enabled = true,  ///< Enabled
            disabled = false  ///< Disabled
        };

        std::optional<int> preset_from_view(const std::string_view &preset) {
#define _CONVERT_(x) \
  if (preset == #x##sv) \
  return x
            _CONVERT_(veryslow);
            _CONVERT_(slower);
            _CONVERT_(slow);
            _CONVERT_(medium);
            _CONVERT_(fast);
            _CONVERT_(faster);
            _CONVERT_(veryfast);
#undef _CONVERT_
            return std::nullopt;
        }

        std::optional<int> coder_from_view(const std::string_view &coder) {
            if (coder == "auto"sv) {
                return _auto;
            }
            if (coder == "cabac"sv || coder == "ac"sv) {
                return disabled;
            }
            if (coder == "cavlc"sv || coder == "vlc"sv) {
                return enabled;
            }
            return std::nullopt;
        }

    }  // namespace qsv

    namespace vt {

        enum coder_e : int {
            _auto = 0,  ///< Auto
            cabac,  ///< CABAC
            cavlc  ///< CAVLC
        };

        int coder_from_view(const std::string_view &coder) {
            if (coder == "auto"sv) {
                return _auto;
            }
            if (coder == "cabac"sv || coder == "ac"sv) {
                return cabac;
            }
            if (coder == "cavlc"sv || coder == "vlc"sv) {
                return cavlc;
            }

            return -1;
        }

        int allow_software_from_view(const std::string_view &software) {
            if (software == "allowed"sv || software == "forced") {
                return 1;
            }

            return 0;
        }

        int force_software_from_view(const std::string_view &software) {
            if (software == "forced") {
                return 1;
            }

            return 0;
        }

        int rt_from_view(const std::string_view &rt) {
            if (rt == "disabled" || rt == "off" || rt == "0") {
                return 0;
            }

            return 1;
        }

    }  // namespace vt

    namespace sw {
        int svtav1_preset_from_view(const std::string_view &preset) {
#define _CONVERT_(x, y) \
  if (preset == #x##sv) \
  return y
            _CONVERT_(veryslow, 1);
            _CONVERT_(slower, 2);
            _CONVERT_(slow, 4);
            _CONVERT_(medium, 5);
            _CONVERT_(fast, 7);
            _CONVERT_(faster, 9);
            _CONVERT_(veryfast, 10);
            _CONVERT_(superfast, 11);
            _CONVERT_(ultrafast, 12);
#undef _CONVERT_
            return 11;  // Default to superfast
        }
    }  // namespace sw

    namespace dd {
        video_t::dd_t::config_option_e config_option_from_view(const std::string_view value) {
#define _CONVERT_(x) \
  if (value == #x##sv) \
  return video_t::dd_t::config_option_e::x
            _CONVERT_(disabled);
            _CONVERT_(verify_only);
            _CONVERT_(ensure_active);
            _CONVERT_(ensure_primary);
            _CONVERT_(ensure_only_display);
#undef _CONVERT_
            return video_t::dd_t::config_option_e::disabled;  // Default to this if value is invalid
        }

        video_t::dd_t::resolution_option_e resolution_option_from_view(const std::string_view value) {
#define _CONVERT_2_ARG_(str, val) \
  if (value == #str##sv) \
  return video_t::dd_t::resolution_option_e::val
#define _CONVERT_(x) _CONVERT_2_ARG_(x, x)
            _CONVERT_(disabled);
            _CONVERT_2_ARG_(auto, automatic);
            _CONVERT_(manual);
#undef _CONVERT_
#undef _CONVERT_2_ARG_
            return video_t::dd_t::resolution_option_e::disabled;  // Default to this if value is invalid
        }

        video_t::dd_t::refresh_rate_option_e refresh_rate_option_from_view(const std::string_view value) {
#define _CONVERT_2_ARG_(str, val) \
  if (value == #str##sv) \
  return video_t::dd_t::refresh_rate_option_e::val
#define _CONVERT_(x) _CONVERT_2_ARG_(x, x)
            _CONVERT_(disabled);
            _CONVERT_2_ARG_(auto, automatic);
            _CONVERT_(manual);
#undef _CONVERT_
#undef _CONVERT_2_ARG_
            return video_t::dd_t::refresh_rate_option_e::disabled;  // Default to this if value is invalid
        }

        video_t::dd_t::hdr_option_e hdr_option_from_view(const std::string_view value) {
#define _CONVERT_2_ARG_(str, val) \
  if (value == #str##sv) \
  return video_t::dd_t::hdr_option_e::val
#define _CONVERT_(x) _CONVERT_2_ARG_(x, x)
            _CONVERT_(disabled);
            _CONVERT_2_ARG_(auto, automatic);
#undef _CONVERT_
#undef _CONVERT_2_ARG_
            return video_t::dd_t::hdr_option_e::disabled;  // Default to this if value is invalid
        }

        video_t::dd_t::mode_remapping_t mode_remapping_from_view(const std::string_view value) {
            const auto parse_entry_list {[](const auto &entry_list, auto &output_field) {
                for (auto &[_, entry] : entry_list) {
                    auto requested_resolution = entry.template get_optional<std::string>("requested_resolution"s);
                    auto requested_fps = entry.template get_optional<std::string>("requested_fps"s);
                    auto final_resolution = entry.template get_optional<std::string>("final_resolution"s);
                    auto final_refresh_rate = entry.template get_optional<std::string>("final_refresh_rate"s);

                    output_field.push_back(video_t::dd_t::mode_remapping_entry_t {
                            requested_resolution.value_or(""),
                            requested_fps.value_or(""),
                            final_resolution.value_or(""),
                            final_refresh_rate.value_or("")
                    });
                }
            }};

            // We need to add a wrapping object to make it valid JSON, otherwise ptree cannot parse it.
            std::stringstream json_stream;
            json_stream << "{\"dd_mode_remapping\":" << value << "}";

            boost::property_tree::ptree json_tree;
            boost::property_tree::read_json(json_stream, json_tree);

            video_t::dd_t::mode_remapping_t output;
            parse_entry_list(json_tree.get_child("dd_mode_remapping.mixed"), output.mixed);
            parse_entry_list(json_tree.get_child("dd_mode_remapping.resolution_only"), output.resolution_only);
            parse_entry_list(json_tree.get_child("dd_mode_remapping.refresh_rate_only"), output.refresh_rate_only);

            return output;
        }
    }  // namespace dd

    video_t video {
            28,  // qp

            0,  // hevc_mode
            0,  // av1_mode

            2,  // min_threads
            {
                    "superfast"s,  // preset
                    "zerolatency"s,  // tune
                    11,  // superfast
            },  // software

//            {},  // nv
            true,  // nv_realtime_hags
            true,  // nv_opengl_vulkan_on_dxgi
            true,  // nv_sunshine_high_power_mode
            {},  // nv_legacy

            {
                    qsv::medium,  // preset
                    qsv::_auto,  // cavlc
                    false,  // slow_hevc
            },  // qsv

            {
                    (int) amd::usage_h264_e::ultralowlatency,  // usage (h264)
                    (int) amd::usage_hevc_e::ultralowlatency,  // usage (hevc)
                    (int) amd::usage_av1_e::ultralowlatency,  // usage (av1)
                    (int) amd::rc_h264_e::vbr_latency,  // rate control (h264)
                    (int) amd::rc_hevc_e::vbr_latency,  // rate control (hevc)
                    (int) amd::rc_av1_e::vbr_latency,  // rate control (av1)
                    0,  // enforce_hrd
                    (int) amd::quality_h264_e::balanced,  // quality (h264)
                    (int) amd::quality_hevc_e::balanced,  // quality (hevc)
                    (int) amd::quality_av1_e::balanced,  // quality (av1)
                    0,  // preanalysis
                    1,  // vbaq
                    (int) amd::coder_e::_auto,  // coder
            },  // amd

            {
                    0,
                    0,
                    1,
                    -1,
            },  // vt

            {
                    false,  // strict_rc_buffer
            },  // vaapi

            {},  // capture
            {},  // encoder
            {},  // adapter_name
            {},  // output_name

            {
                    video_t::dd_t::config_option_e::disabled,  // configuration_option
                    video_t::dd_t::resolution_option_e::automatic,  // resolution_option
                    {},  // manual_resolution
                    video_t::dd_t::refresh_rate_option_e::automatic,  // refresh_rate_option
                    {},  // manual_refresh_rate
                    video_t::dd_t::hdr_option_e::automatic,  // hdr_option
                    3s,  // config_revert_delay
                    {},  // config_revert_on_disconnect
                    {},  // mode_remapping
                    {}  // wa
            },  // display_device

            1,  // min_fps_factor
            0  // max_bitrate
    };

    audio_t audio {
            {},  // audio_sink
            {},  // virtual_sink
            true,  // install_steam_drivers
    };

    stream_t stream {
            10s,  // ping_timeout

            APPS_JSON_PATH,

            20,  // fecPercentage

            ENCRYPTION_MODE_NEVER,  // lan_encryption_mode
            ENCRYPTION_MODE_OPPORTUNISTIC,  // wan_encryption_mode
    };

    nvhttp_t nvhttp {
            "lan",  // origin web manager

            PRIVATE_KEY_FILE,
            CERTIFICATE_FILE,

            platf::get_host_name(),  // sunshine_name,
            "sunshine_state.json"s,  // file_state
            {},  // external_ip
    };

    input_t input {
            {
                    {0x10, 0xA0},
                    {0x11, 0xA2},
                    {0x12, 0xA4},
            },
            -1ms,  // back_button_timeout
            500ms,  // key_repeat_delay
            std::chrono::duration<double> {1 / 24.9},  // key_repeat_period

            {
                    "",
                    0,
            },  // Default gamepad
            true,  // back as touchpad click enabled (manual DS4 only)
            true,  // client gamepads with motion events are emulated as DS4
            true,  // client gamepads with touchpads are emulated as DS4

            true,  // keyboard enabled
            true,  // mouse enabled
            true,  // controller enabled
            true,  // always send scancodes
            true,  // high resolution scrolling
            true,  // native pen/touch support
    };

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

    bool endline(char ch) {
        return ch == '\r' || ch == '\n';
    }

    bool space_tab(char ch) {
        return ch == ' ' || ch == '\t';
    }

    bool whitespace(char ch) {
        return space_tab(ch) || endline(ch);
    }

    std::string to_string(const char *begin, const char *end) {
        std::string result;

        KITTY_WHILE_LOOP(auto pos = begin, pos != end, {
            auto comment = std::find(pos, end, '#');
            auto endl = std::find_if(comment, end, endline);

            result.append(pos, comment);

            pos = endl;
        })

        return result;
    }

    template<class It>
    It skip_list(It skipper, It end) {
        int stack = 1;
        while (skipper != end && stack) {
            if (*skipper == '[') {
                ++stack;
            }
            if (*skipper == ']') {
                --stack;
            }

            ++skipper;
        }

        return skipper;
    }

    std::pair<
            std::string_view::const_iterator,
            std::optional<std::pair<std::string, std::string>>>
    parse_option(std::string_view::const_iterator begin, std::string_view::const_iterator end) {
        begin = std::find_if_not(begin, end, whitespace);
        auto endl = std::find_if(begin, end, endline);
        auto endc = std::find(begin, endl, '#');
        endc = std::find_if(std::make_reverse_iterator(endc), std::make_reverse_iterator(begin), std::not_fn(whitespace)).base();

        auto eq = std::find(begin, endc, '=');
        if (eq == endc || eq == begin) {
            return std::make_pair(endl, std::nullopt);
        }

        auto end_name = std::find_if_not(std::make_reverse_iterator(eq), std::make_reverse_iterator(begin), space_tab).base();
        auto begin_val = std::find_if_not(eq + 1, endc, space_tab);

        if (begin_val == endl) {
            return std::make_pair(endl, std::nullopt);
        }

        // Lists might contain newlines
        if (*begin_val == '[') {
            endl = skip_list(begin_val + 1, end);
            if (endl == end) {
                std::cout << "Warning: Config option ["sv << to_string(begin, end_name) << "] Missing ']'"sv;

                return std::make_pair(endl, std::nullopt);
            }
        }

        return std::make_pair(
                endl,
                std::make_pair(to_string(begin, end_name), to_string(begin_val, endl))
        );
    }

    std::unordered_map<std::string, std::string> parse_config(const std::string_view &file_content) {
        std::unordered_map<std::string, std::string> vars;

        auto pos = std::begin(file_content);
        auto end = std::end(file_content);

        while (pos < end) {
            // auto newline = std::find_if(pos, end, [](auto ch) { return ch == '\n' || ch == '\r'; });
            TUPLE_2D(endl, var, parse_option(pos, end));

            pos = endl;
            if (pos != end) {
                pos += (*pos == '\r') ? 2 : 1;
            }

            if (!var) {
                continue;
            }

            vars.emplace(std::move(*var));
        }

        return vars;
    }

    void string_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::string &input) {
        auto it = vars.find(name);
        if (it == std::end(vars)) {
            return;
        }

        input = std::move(it->second);

        vars.erase(it);
    }

    template<typename T, typename F>
    void generic_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, T &input, F &&f) {
        std::string tmp;
        string_f(vars, name, tmp);
        if (!tmp.empty()) {
            input = f(tmp);
        }
    }

    void string_restricted_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::string &input, const std::vector<std::string_view> &allowed_vals) {
        std::string temp;
        string_f(vars, name, temp);

        for (auto &allowed_val : allowed_vals) {
            if (temp == allowed_val) {
                input = std::move(temp);
                return;
            }
        }
    }

    void path_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, fs::path &input) {
        // appdata needs to be retrieved once only
        static auto appdata = platf::appdata();

        std::string temp;
        string_f(vars, name, temp);

        if (!temp.empty()) {
            input = temp;
        }

        if (input.is_relative()) {
            input = appdata / input;
        }

        auto dir = input;
        dir.remove_filename();

        // Ensure the directories exists
        if (!fs::exists(dir)) {
            fs::create_directories(dir);
        }
    }

    void path_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::string &input) {
        fs::path temp = input;

        path_f(vars, name, temp);

        input = temp.string();
    }

    void int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, int &input) {
        auto it = vars.find(name);

        if (it == std::end(vars)) {
            return;
        }

        std::string_view val = it->second;

        // If value is something like: "756" instead of 756
        if (val.size() >= 2 && val[0] == '"') {
            val = val.substr(1, val.size() - 2);
        }

        // If that integer is in hexadecimal
        if (val.size() >= 2 && val.substr(0, 2) == "0x"sv) {
            input = util::from_hex<int>(val.substr(2));
        } else {
            input = util::from_view(val);
        }

        vars.erase(it);
    }

    void int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::optional<int> &input) {
        auto it = vars.find(name);

        if (it == std::end(vars)) {
            return;
        }

        std::string_view val = it->second;

        // If value is something like: "756" instead of 756
        if (val.size() >= 2 && val[0] == '"') {
            val = val.substr(1, val.size() - 2);
        }

        // If that integer is in hexadecimal
        if (val.size() >= 2 && val.substr(0, 2) == "0x"sv) {
            input = util::from_hex<int>(val.substr(2));
        } else {
            input = util::from_view(val);
        }

        vars.erase(it);
    }

    template<class F>
    void int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, int &input, F &&f) {
        std::string tmp;
        string_f(vars, name, tmp);
        if (!tmp.empty()) {
            input = f(tmp);
        }
    }

    template<class F>
    void int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::optional<int> &input, F &&f) {
        std::string tmp;
        string_f(vars, name, tmp);
        if (!tmp.empty()) {
            input = f(tmp);
        }
    }

    void int_between_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, int &input, const std::pair<int, int> &range) {
        int temp = input;

        int_f(vars, name, temp);

        TUPLE_2D_REF(lower, upper, range);
        if (temp >= lower && temp <= upper) {
            input = temp;
        }
    }

    bool to_bool(std::string &boolean) {
        std::for_each(std::begin(boolean), std::end(boolean), [](char ch) {
            return (char) std::tolower(ch);
        });

        return boolean == "true"sv ||
               boolean == "yes"sv ||
               boolean == "enable"sv ||
               boolean == "enabled"sv ||
               boolean == "on"sv ||
               (std::find(std::begin(boolean), std::end(boolean), '1') != std::end(boolean));
    }

    void bool_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, bool &input) {
        std::string tmp;
        string_f(vars, name, tmp);

        if (tmp.empty()) {
            return;
        }

        input = to_bool(tmp);
    }

    void double_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, double &input) {
        std::string tmp;
        string_f(vars, name, tmp);

        if (tmp.empty()) {
            return;
        }

        char *c_str_p;
        auto val = std::strtod(tmp.c_str(), &c_str_p);

        if (c_str_p == tmp.c_str()) {
            return;
        }

        input = val;
    }

    void double_between_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, double &input, const std::pair<double, double> &range) {
        double temp = input;

        double_f(vars, name, temp);

        TUPLE_2D_REF(lower, upper, range);
        if (temp >= lower && temp <= upper) {
            input = temp;
        }
    }

    void list_string_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::vector<std::string> &input) {
        std::string string;
        string_f(vars, name, string);

        if (string.empty()) {
            return;
        }

        input.clear();

        auto begin = std::cbegin(string);
        if (*begin == '[') {
            ++begin;
        }

        begin = std::find_if_not(begin, std::cend(string), whitespace);
        if (begin == std::cend(string)) {
            return;
        }

        auto pos = begin;
        while (pos < std::cend(string)) {
            if (*pos == '[') {
                pos = skip_list(pos + 1, std::cend(string)) + 1;
            } else if (*pos == ']') {
                break;
            } else if (*pos == ',') {
                input.emplace_back(begin, pos);
                pos = begin = std::find_if_not(pos + 1, std::cend(string), whitespace);
            } else {
                ++pos;
            }
        }

        if (pos != begin) {
            input.emplace_back(begin, pos);
        }
    }

    void list_prep_cmd_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::vector<prep_cmd_t> &input) {
        std::string string;
        string_f(vars, name, string);

        std::stringstream jsonStream;

        // check if string is empty, i.e. when the value doesn't exist in the config file
        if (string.empty()) {
            return;
        }

        // We need to add a wrapping object to make it valid JSON, otherwise ptree cannot parse it.
        jsonStream << "{\"prep_cmd\":" << string << "}";

        boost::property_tree::ptree jsonTree;
        boost::property_tree::read_json(jsonStream, jsonTree);

        for (auto &[_, prep_cmd] : jsonTree.get_child("prep_cmd"s)) {
            auto do_cmd = prep_cmd.get_optional<std::string>("do"s);
            auto undo_cmd = prep_cmd.get_optional<std::string>("undo"s);
            auto elevated = prep_cmd.get_optional<bool>("elevated"s);

            input.emplace_back(do_cmd.value_or(""), undo_cmd.value_or(""), elevated.value_or(false));
        }
    }

    void list_int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::vector<int> &input) {
        std::vector<std::string> list;
        list_string_f(vars, name, list);

        // check if list is empty, i.e. when the value doesn't exist in the config file
        if (list.empty()) {
            return;
        }

        // The framerate list must be cleared before adding values from the file configuration.
        // If the list is not cleared, then the specified parameters do not affect the behavior of the sunshine server.
        // That is, if you set only 30 fps in the configuration file, it will not work because by default, during initialization the list includes 10, 30, 60, 90 and 120 fps.
        input.clear();
        for (auto &el : list) {
            std::string_view val = el;

            // If value is something like: "756" instead of 756
            if (val.size() >= 2 && val[0] == '"') {
                val = val.substr(1, val.size() - 2);
            }

            int tmp;

            // If the integer is a hexadecimal
            if (val.size() >= 2 && val.substr(0, 2) == "0x"sv) {
                tmp = util::from_hex<int>(val.substr(2));
            } else {
                tmp = util::from_view(val);
            }
            input.emplace_back(tmp);
        }
    }

    void map_int_int_f(std::unordered_map<std::string, std::string> &vars, const std::string &name, std::unordered_map<int, int> &input) {
        std::vector<int> list;
        list_int_f(vars, name, list);

        // The list needs to be a multiple of 2
        if (list.size() % 2) {
            std::cout << "Warning: expected "sv << name << " to have a multiple of two elements --> not "sv << list.size() << std::endl;
            return;
        }

        int x = 0;
        while (x < list.size()) {
            auto key = list[x++];
            auto val = list[x++];

            input.emplace(key, val);
        }
    }

    std::vector<std::string_view> &get_supported_gamepad_options() {
        const auto options = platf::supported_gamepads(nullptr);
        static std::vector<std::string_view> opts {};
        opts.reserve(options.size());
        for (auto &opt : options) {
            opts.emplace_back(opt.name);
        }
        return opts;
    }
}  // namespace config
