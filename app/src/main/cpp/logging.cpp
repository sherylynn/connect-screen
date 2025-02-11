/**
 * @file src/logging.cpp
 * @brief Definitions for logging related functions.
 */
// standard includes
#include <fstream>
#include <iomanip>
#include <iostream>

// lib includes
#include <boost/core/null_deleter.hpp>
#include <boost/format.hpp>
#include <boost/log/attributes/clock.hpp>
#include <boost/log/common.hpp>
#include <boost/log/expressions.hpp>
#include <boost/log/sinks.hpp>
#include <boost/log/sources/severity_logger.hpp>

// local includes
#include "logging.h"

// 添加 Android 日志头文件
#include <android/log.h>

using namespace std::literals;

namespace bl = boost::log;

boost::shared_ptr<boost::log::sinks::asynchronous_sink<boost::log::sinks::text_ostream_backend>> sink;

bl::sources::severity_logger<int> verbose(0);  // Dominating output
bl::sources::severity_logger<int> debug(1);  // Follow what is happening
bl::sources::severity_logger<int> info(2);  // Should be informed about
bl::sources::severity_logger<int> warning(3);  // Strange events
bl::sources::severity_logger<int> error(4);  // Recoverable errors
bl::sources::severity_logger<int> fatal(5);  // Unrecoverable errors
#ifdef SUNSHINE_TESTS
bl::sources::severity_logger<int> tests(10);  // Automatic tests output
#endif

BOOST_LOG_ATTRIBUTE_KEYWORD(severity, "Severity", int)

namespace logging {
    deinit_t::~deinit_t() {
        deinit();
    }

    void deinit() {
        log_flush();
        bl::core::get()->remove_sink(sink);
        sink.reset();
    }

    void formatter(const boost::log::record_view &view, boost::log::formatting_ostream &os) {
        constexpr const char *message = "Message";
        constexpr const char *severity = "Severity";

        auto log_level = view.attribute_values()[severity].extract<int>().get();

        // 修改日志级别映射到 Android 日志级别
        android_LogPriority android_priority;
        std::string_view log_type;
        switch (log_level) {
            case 0:
                android_priority = ANDROID_LOG_VERBOSE;
                log_type = "Verbose: "sv;
                break;
            case 1:
                android_priority = ANDROID_LOG_DEBUG;
                log_type = "Debug: "sv;
                break;
            case 2:
                android_priority = ANDROID_LOG_INFO;
                log_type = "Info: "sv;
                break;
            case 3:
                android_priority = ANDROID_LOG_WARN;
                log_type = "Warning: "sv;
                break;
            case 4:
                android_priority = ANDROID_LOG_ERROR;
                log_type = "Error: "sv;
                break;
            case 5:
                android_priority = ANDROID_LOG_FATAL;
                log_type = "Fatal: "sv;
                break;
        };

        // 获取日志消息
        std::string log_message = view.attribute_values()[message].extract<std::string>().get();
        
        // 输出到 Android 日志系统
        __android_log_print(android_priority, "Sunshine", "%s%s", log_type.data(), log_message.c_str());
    }

    [[nodiscard]] std::unique_ptr<deinit_t> init(int min_log_level) {
        if (sink) {
            // Deinitialize the logging system before reinitializing it. This can probably only ever be hit in tests.
            deinit();
        }

        sink = boost::make_shared<text_sink>();
        boost::shared_ptr<std::ostream> stream {&std::cout, boost::null_deleter()};
        sink->locked_backend()->add_stream(stream);
        sink->set_filter(severity >= min_log_level);
        sink->set_formatter(&formatter);

        // Flush after each log record to ensure log file contents on disk isn't stale.
        // This is particularly important when running from a Windows service.
        sink->locked_backend()->auto_flush(true);

        bl::core::get()->add_sink(sink);
        return std::make_unique<deinit_t>();
    }

    void log_flush() {
        if (sink) {
            sink->flush();
        }
    }

    void print_help(const char *name) {
        std::cout
                << "Usage: "sv << name << " [options] [/path/to/configuration_file] [--cmd]"sv << std::endl
                << "    Any configurable option can be overwritten with: \"name=value\""sv << std::endl
                << std::endl
                << "    Note: The configuration will be created if it doesn't exist."sv << std::endl
                << std::endl
                << "    --help                    | print help"sv << std::endl
                << "    --creds username password | set user credentials for the Web manager"sv << std::endl
                << "    --version                 | print the version of sunshine"sv << std::endl
                << std::endl
                << "    flags"sv << std::endl
                << "        -0 | Read PIN from stdin"sv << std::endl
                << "        -1 | Do not load previously saved state and do retain any state after shutdown"sv << std::endl
                << "           | Effectively starting as if for the first time without overwriting any pairings with your devices"sv << std::endl
                << "        -2 | Force replacement of headers in video stream"sv << std::endl
                << "        -p | Enable/Disable UPnP"sv << std::endl
                << std::endl;
    }

    std::string bracket(const std::string &input) {
        return "["s + input + "]"s;
    }

    std::wstring bracket(const std::wstring &input) {
        return L"["s + input + L"]"s;
    }

}  // namespace logging
