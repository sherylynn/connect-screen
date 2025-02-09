#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include <enet/enet.h>
#include "stream.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ServerNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ServerNative", __VA_ARGS__)

static bool running = false;

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_startServer(JNIEnv* env, jobject /* this */) {
    if (!running) {
        running = true;
        stream::start();
        LOGI("Server thread started");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        LOGI("Server thread stopped");
    }
} 