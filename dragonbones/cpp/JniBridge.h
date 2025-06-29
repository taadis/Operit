#ifndef DRAGONBONES_JNIBRIDGE_H
#define DRAGONBONES_JNIBRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_init(JNIEnv *env, jclass clazz, jobject asset_manager);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_loadDragonBones(JNIEnv *env, jclass clazz, jstring model_path, jstring texture_path);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onPause(JNIEnv *env, jclass clazz);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onResume(JNIEnv *env, jclass clazz);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onDestroy(JNIEnv *env, jclass clazz);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onSurfaceCreated(JNIEnv *env, jclass clazz);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onSurfaceChanged(JNIEnv *env, jclass clazz, jint width, jint height);

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onDrawFrame(JNIEnv *env, jclass clazz);

#ifdef __cplusplus
}
#endif

#endif //DRAGONBONES_JNIBRIDGE_H 