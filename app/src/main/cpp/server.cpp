#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include <enet/enet.h>
#include "stream.h"
#include "logging.h"
#include "globals.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ServerNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ServerNative", __VA_ARGS__)

static bool running = false;

static std::unique_ptr<logging::deinit_t> deinit;

//static std::shared_ptr<stream::session_t> session;

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_startServer(JNIEnv* env, jobject /* this */) {
    if (!running) {
        running = true;
        mail::man = std::make_shared<safe::mail_raw_t>();
        deinit = logging::init(0);
        stream::session::launch_session_t launch_session = {};
        stream::config_t config = {};
        std::shared_ptr<stream::session_t> session = stream::session::alloc(config, launch_session);
//        stream::start();
        LOGI("Server thread started!!!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        LOGI("Server thread stopped");
        deinit = nullptr;
    }
} 