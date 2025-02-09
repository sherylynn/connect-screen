#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include <enet/enet.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ServerNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ServerNative", __VA_ARGS__)

static bool running = false;
static std::thread server_thread;
static ENetHost* server = nullptr;

void server_loop() {
    if (enet_initialize() != 0) {
        LOGE("ENet initialization failed");
        return;
    }
    if (server != nullptr) {
        enet_host_destroy(server);
    }
    enet_deinitialize();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_startServer(JNIEnv* env, jobject /* this */) {
    if (!running) {
        running = true;
        server_thread = std::thread(server_loop);
        LOGI("Server thread started");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        if (server_thread.joinable()) {
            server_thread.join();
        }
        LOGI("Server thread stopped");
    }
} 