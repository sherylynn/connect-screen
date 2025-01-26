#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}