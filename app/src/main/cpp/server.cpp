#include <jni.h>
#include <string>
#include <thread>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ServerNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ServerNative", __VA_ARGS__)

static bool running = false;
static std::thread server_thread;

void server_loop() {
    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOGE("Socket creation failed");
        return;
    }

    struct sockaddr_in address;
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(7100);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        LOGE("Bind failed");
        close(server_fd);
        return;
    }

    if (listen(server_fd, 3) < 0) {
        LOGE("Listen failed");
        close(server_fd);
        return;
    }

    LOGI("Server started on port 7100");

    while (running) {
        int new_socket = accept(server_fd, nullptr, nullptr);
        if (new_socket < 0) {
            continue;
        }

        char buffer[1024] = {0};
        read(new_socket, buffer, 1024);
        
        if (strcmp(buffer, "hello") == 0) {
            const char *response = "world";
            send(new_socket, response, strlen(response), 0);
        }
        
        close(new_socket);
    }

    close(server_fd);
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