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
static JavaVM* javaVM = nullptr;
static jobject serverInstance = nullptr;
static std::unique_ptr<logging::deinit_t> deinit;
static std::shared_ptr<stream::session_t> sessionObj;

// JNI_OnLoad implementation
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    javaVM = vm;
    return JNI_VERSION_1_6;
}

static stream::config_t create_config(const std::string& input) {
    stream::config_t config = {};

    // 设置默认值
    std::unordered_map<std::string, std::string> args = {
            {"x-nv-video[0].encoderCscMode", "0"},
            {"x-nv-vqos[0].bitStreamFormat", "0"},
            {"x-nv-video[0].dynamicRangeMode", "0"},
            {"x-nv-aqos.packetDuration", "5"},
            {"x-nv-general.useReliableUdp", "1"},
            {"x-nv-vqos[0].fec.minRequiredFecPackets", "0"},
            {"x-nv-general.featureFlags", "135"},
            {"x-ml-general.featureFlags", "0"},
            {"x-nv-vqos[0].qosTrafficType", "5"},
            {"x-nv-aqos.qosTrafficType", "4"},
            {"x-ml-video.configuredBitrateKbps", "0"},
            {"x-ss-general.encryptionEnabled", "0"},
            {"x-ss-video[0].chromaSamplingType", "0"},
            {"x-ss-video[0].intraRefresh", "0"},
            {"x-nv-audio.surround.numChannels", "2"},
            {"x-nv-audio.surround.channelMask", "3"},
            {"x-nv-audio.surround.AudioQuality", "0"},
            {"x-nv-video[0].packetSize", "1392"},
            {"x-nv-video[0].clientViewportHt", "1080"},
            {"x-nv-video[0].clientViewportWd", "1920"},
            {"x-nv-video[0].maxFPS", "60"},
            {"x-nv-vqos[0].bw.maximumBitrateKbps", "10000"},
            {"x-nv-video[0].videoEncoderSlicesPerFrame", "1"},
            {"x-nv-video[0].maxNumReferenceFrames", "0"}
    };

    // 解析输入字符串
    std::istringstream iss(input);
    std::string line;
    while (std::getline(iss, line)) {
        auto type = line.substr(0, 2);
        if (type == "a=") {
            auto pos = line.find(':');
            if (pos != std::string::npos) {
                auto name = line.substr(2, pos - 2);
                auto val = line.substr(pos + 1);
                if (!val.empty() && val.back() == ' ') {
                    val.pop_back();
                }
                args[name] = val;
            }
        }
    }

    // 设置配置参数
    try {
        config.controlProtocolType = std::stoi(std::string(args.at("x-nv-general.useReliableUdp")));
        config.minRequiredFecPackets = std::stoi(std::string(args.at("x-nv-vqos[0].fec.minRequiredFecPackets")));
        config.mlFeatureFlags = std::stoi(std::string(args.at("x-ml-general.featureFlags")));
        config.audioQosType = std::stoi(std::string(args.at("x-nv-aqos.qosTrafficType")));
        config.videoQosType = std::stoi(std::string(args.at("x-nv-vqos[0].qosTrafficType")));

        // 设置显示器相关参数
        config.monitor.encoderCscMode = std::stoi(std::string(args.at("x-nv-video[0].encoderCscMode")));
        config.monitor.videoFormat = std::stoi(std::string(args.at("x-nv-vqos[0].bitStreamFormat")));
        config.monitor.dynamicRange = std::stoi(std::string(args.at("x-nv-video[0].dynamicRangeMode")));
        config.monitor.chromaSamplingType = std::stoi(std::string(args.at("x-ss-video[0].chromaSamplingType")));
        config.monitor.enableIntraRefresh = std::stoi(std::string(args.at("x-ss-video[0].intraRefresh")));

        // 添加音频相关配置
        config.audio.channels = std::stoi(std::string(args.at("x-nv-audio.surround.numChannels")));
        config.audio.mask = std::stoi(std::string(args.at("x-nv-audio.surround.channelMask")));
        config.audio.packetDuration = std::stoi(std::string(args.at("x-nv-aqos.packetDuration")));
        config.audio.flags[audio::config_t::HIGH_QUALITY] = 
            std::stoi(std::string(args.at("x-nv-audio.surround.AudioQuality")));

        // 添加视频相关配置
        config.packetsize = std::stoi(std::string(args.at("x-nv-video[0].packetSize")));
        config.monitor.height = std::stoi(std::string(args.at("x-nv-video[0].clientViewportHt")));
        config.monitor.width = std::stoi(std::string(args.at("x-nv-video[0].clientViewportWd")));
        config.monitor.framerate = std::stoi(std::string(args.at("x-nv-video[0].maxFPS")));
        config.monitor.bitrate = std::stoi(std::string(args.at("x-nv-vqos[0].bw.maximumBitrateKbps")));
        config.monitor.slicesPerFrame = std::stoi(std::string(args.at("x-nv-video[0].videoEncoderSlicesPerFrame")));
        config.monitor.numRefFrames = std::stoi(std::string(args.at("x-nv-video[0].maxNumReferenceFrames")));

    } catch (const std::exception& e) {
        LOGE("Error parsing config: %s", e.what());
    }

    return config;
}

// 回调辅助函数
namespace stream {
    void notifyMoonlightConnected() {
        JNIEnv *env;
        if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            jclass serverClass = env->GetObjectClass(serverInstance);
            jmethodID methodId = env->GetMethodID(serverClass, "onMoonlightConnected", "()V");

            if (methodId != nullptr) {
                env->CallVoidMethod(serverInstance, methodId);
            }

            env->DeleteLocalRef(serverClass);
            javaVM->DetachCurrentThread();
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_startServer(JNIEnv* env, jobject thiz, jbyteArray gcmKey, jbyteArray iv, jstring peerIp, jstring configStr) {
    // 保存全局引用
    serverInstance = env->NewGlobalRef(thiz);
    
    if (!running) {
        running = true;
        mail::man = std::make_shared<safe::mail_raw_t>();

        stream::session::launch_session_t launch_session = {
                .av_ping_payload = "A4AACADDA6340FB4"
        };
        
        // 获取configStr并转换为config
        const char* configChars = env->GetStringUTFChars(configStr, nullptr);
        stream::config_t config = create_config(configChars);
        env->ReleaseStringUTFChars(configStr, configChars);

        jbyte* gcmKeyBytes = env->GetByteArrayElements(gcmKey, nullptr);
        jsize gcmKeyLength = env->GetArrayLength(gcmKey);

        if (gcmKeyLength > 0) {
            launch_session.gcm_key.resize(gcmKeyLength);
            std::memcpy(launch_session.gcm_key.data(), gcmKeyBytes, gcmKeyLength);
        }
        launch_session.control_connect_data = 2207506894;

        env->ReleaseByteArrayElements(gcmKey, gcmKeyBytes, JNI_ABORT);

        // 获取iv数组数据
        jbyte* ivBytes = env->GetByteArrayElements(iv, nullptr);
        jsize ivLength = env->GetArrayLength(iv);
        
        // 将iv数据复制到launch_session中
        if (ivLength > 0) {
            launch_session.iv.resize(ivLength);
            std::memcpy(launch_session.iv.data(), ivBytes, ivLength);
        }
        
        // 释放iv数组
        env->ReleaseByteArrayElements(iv, ivBytes, JNI_ABORT);

        // 获取peerIp字符串
        const char* peerIpStr = env->GetStringUTFChars(peerIp, nullptr);

        sessionObj = stream::session::alloc(config, launch_session);
        stream::session::start(*sessionObj, peerIpStr);
        
        // 释放peerIp字符串
        env->ReleaseStringUTFChars(peerIp, peerIpStr);
        
        LOGI("Server thread started!!!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        if (serverInstance != nullptr) {
            env->DeleteGlobalRef(serverInstance);
            serverInstance = nullptr;
        }
        LOGI("Server thread stopped");
        deinit = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_postFrame(
    JNIEnv* env, jobject /* this */,
    jbyteArray frameData, jboolean isIdr, jlong frameIndex) {

    // 获取帧数据
    jbyte* bytes = env->GetByteArrayElements(frameData, nullptr);
    jsize length = env->GetArrayLength(frameData);

    // 创建帧数据向量
    std::vector<uint8_t> frame_vector(length);
    std::memcpy(frame_vector.data(), bytes, length);

    // 释放Java字节数组
    env->ReleaseByteArrayElements(frameData, bytes, JNI_ABORT);
    auto packet = std::make_unique<video::packet_raw_generic>(
            std::move(frame_vector),
            frameIndex,
            isIdr
    );
    packet->channel_data = sessionObj.get();

    LOGI("received postFrame");
    stream::postFrame(std::move(packet));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gitee_connect_1screen_NativeServer_ping(JNIEnv* env, jobject /* this */) {
    if(!deinit) {
        deinit = logging::init(0);
    }
    BOOST_LOG(debug) << "check is logging ok";
    return 0;
}