#pragma once

#include <bitset>
#include <filesystem>
#include <functional>
#include <mutex>
#include <string>
#include "utility.h"

namespace video {

    struct packet_raw_t {
        virtual ~packet_raw_t() = default;

        virtual bool is_idr() = 0;

        virtual int64_t frame_index() = 0;

        virtual uint8_t *data() = 0;

        virtual size_t data_size() = 0;

        struct replace_t {
            std::string_view old;
            std::string_view _new;

            KITTY_DEFAULT_CONSTR_MOVE(replace_t)

            replace_t(std::string_view old, std::string_view _new) noexcept:
                    old {std::move(old)},
                    _new {std::move(_new)} {
            }
        };

        std::vector<replace_t> *replacements = nullptr;
        void *channel_data = nullptr;
        bool after_ref_frame_invalidation = false;
        std::optional<std::chrono::steady_clock::time_point> frame_timestamp;
    };

    using packet_t = std::unique_ptr<packet_raw_t>;
}