#include "JniBridge.h"
#include <android/log.h>
#include "dragonBones/DragonBonesHeaders.h"
#include "opengl/OpenGLFactory.h"
#include "opengl/OpenGLSlot.h"
#include <GLES2/gl2.h>
#include <string>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <vector>
#include <cstring>
#include "rapidjson/document.h"
#include "rapidjson/error/en.h"

#define LOG_TAG "DragonBonesJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
    struct JniBridgeInstance {
        dragonBones::WorldClock* worldClock = nullptr;
        dragonBones::Armature* armature = nullptr;
        dragonBones::opengl::OpenGLFactory* factory = nullptr;
        
        // OpenGL ES 2.0 rendering variables
        GLuint programId = 0;
        GLint positionLocation = -1;
        GLint texCoordLocation = -1;
        GLint mvpMatrixLocation = -1;
        GLint textureLocation = -1;
        GLfloat projectionMatrix[16];

        // Buffers to hold asset data, loaded off the GL thread
        std::vector<char> dragonBonesDataBuffer;
        std::vector<char> textureJsonBuffer;
        std::vector<char> texturePngDataBuffer;
        bool assetsLoaded = false;

        ~JniBridgeInstance() {
            if (worldClock) {
                delete worldClock;
                worldClock = nullptr;
            }
            if (factory) {
                delete factory;
                factory = nullptr;
            }
            
            if (programId) {
                glDeleteProgram(programId);
                programId = 0;
            }
        }
    };

    JniBridgeInstance* instance = nullptr;

    JniBridgeInstance* getInstance() {
        if (!instance) {
            instance = new JniBridgeInstance();
        }
        return instance;
    }
    
    // 辅助函数：创建和编译着色器
    GLuint compileShader(GLenum type, const char* source) {
        GLuint shader = glCreateShader(type);
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);
        
        // 检查编译状态
        GLint success;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
        if (!success) {
            GLchar infoLog[512];
            glGetShaderInfoLog(shader, sizeof(infoLog), nullptr, infoLog);
            LOGE("Shader compilation failed: %s", infoLog);
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
    
    // 辅助函数：创建着色器程序
    GLuint createShaderProgram() {
        // 顶点着色器
        const char* vertexShaderSource = 
            "attribute vec2 a_position;\n"
            "attribute vec2 a_texCoord;\n"
            "varying vec2 v_texCoord;\n"
            "uniform mat4 u_mvpMatrix;\n"
            "void main() {\n"
            "  gl_Position = u_mvpMatrix * vec4(a_position, 0.0, 1.0);\n"
            "  v_texCoord = a_texCoord;\n"
            "}\n";
        
        // 片段着色器
        const char* fragmentShaderSource = 
            "precision mediump float;\n"
            "varying vec2 v_texCoord;\n"
            "uniform sampler2D u_texture;\n"
            "void main() {\n"
            "  gl_FragColor = texture2D(u_texture, v_texCoord);\n"
            "}\n";
        
        GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
        if (!vertexShader) return 0;
        
        GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource);
        if (!fragmentShader) {
            glDeleteShader(vertexShader);
            return 0;
        }
        
        GLuint program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        
        GLint success;
        glGetProgramiv(program, GL_LINK_STATUS, &success);
        if (!success) {
            GLchar infoLog[512];
            glGetProgramInfoLog(program, sizeof(infoLog), nullptr, infoLog);
            LOGE("Shader program linking failed: %s", infoLog);
            glDeleteProgram(program);
            program = 0;
        }
        
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        
        return program;
    }
    
    // 辅助函数：创建正交投影矩阵
    void createOrthographicMatrix(float left, float right, float bottom, float top, float near, float far, float* matrix) {
        // 列主序矩阵
        matrix[0] = 2.0f / (right - left);
        matrix[1] = 0.0f;
        matrix[2] = 0.0f;
        matrix[3] = 0.0f;
        
        matrix[4] = 0.0f;
        matrix[5] = 2.0f / (top - bottom);
        matrix[6] = 0.0f;
        matrix[7] = 0.0f;
        
        matrix[8] = 0.0f;
        matrix[9] = 0.0f;
        matrix[10] = -2.0f / (far - near);
        matrix[11] = 0.0f;
        
        matrix[12] = -(right + left) / (right - left);
        matrix[13] = -(top + bottom) / (top - bottom);
        matrix[14] = -(far + near) / (far - near);
        matrix[15] = 1.0f;
    }
    
    void createIdentityMatrix(float* matrix) {
        matrix[0] = 1.0f; matrix[4] = 0.0f; matrix[8] = 0.0f; matrix[12] = 0.0f;
        matrix[1] = 0.0f; matrix[5] = 1.0f; matrix[9] = 0.0f; matrix[13] = 0.0f;
        matrix[2] = 0.0f; matrix[6] = 0.0f; matrix[10] = 1.0f; matrix[14] = 0.0f;
        matrix[3] = 0.0f; matrix[7] = 0.0f; matrix[11] = 0.0f; matrix[15] = 1.0f;
    }

    void createTranslateMatrix(float* matrix, float tx, float ty, float tz) {
        createIdentityMatrix(matrix);
        matrix[12] = tx;
        matrix[13] = ty;
        matrix[14] = tz;
    }
    
    void createScaleMatrix(float* matrix, float sx, float sy, float sz) {
        createIdentityMatrix(matrix);
        matrix[0] = sx;
        matrix[5] = sy;
        matrix[10] = sz;
    }
    
    // 辅助函数：将 DragonBones 2D 矩阵转换为 OpenGL 4x4 矩阵
    void convertDBMatrixToGL(const dragonBones::Matrix& dbMatrix, float* glMatrix) {
        createIdentityMatrix(glMatrix);
        glMatrix[0] = dbMatrix.a;
        glMatrix[1] = dbMatrix.b;
        glMatrix[4] = dbMatrix.c;
        glMatrix[5] = dbMatrix.d;
        glMatrix[12] = dbMatrix.tx;
        glMatrix[13] = dbMatrix.ty;
    }

    // 辅助函数：矩阵乘法
    void multiplyMatrices(const float* a, const float* b, float* result) {
        float res[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                res[j * 4 + i] = 0.0f;
                for (int k = 0; k < 4; k++) {
                    res[j * 4 + i] += a[k * 4 + i] * b[j * 4 + k];
                }
            }
        }
        memcpy(result, res, sizeof(res));
    }
}

// Global variables for DragonBones
static float viewportWidth = 0.0f;
static float viewportHeight = 0.0f;
static AAssetManager* assetManager = nullptr;

// Helper function to get the asset path
std::string getAssetPath(const std::string& path) {
    if (assetManager == nullptr) {
        LOGE("AssetManager is null");
        return "";
    }

    // The asset path in the APK should not have the "file:///android_asset/" prefix
    std::string assetPath = path;
    const std::string prefix = "file:///android_asset/";
    if (path.rfind(prefix, 0) == 0) {
        assetPath = path.substr(prefix.length());
    }

    return assetPath;
}

// Helper function to read a file from assets
std::vector<char> readFileFromAssets(const std::string& path) {
    if (!assetManager) {
        LOGE("AssetManager is not initialized");
        return {};
    }

    std::string assetPath = path;
    const std::string prefix = "file:///android_asset/";
    if (path.rfind(prefix, 0) == 0) {
        assetPath = path.substr(prefix.length());
    }

    AAsset* asset = AAssetManager_open(assetManager, assetPath.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open asset: %s", assetPath.c_str());
        return {};
    }

    off_t assetLength = AAsset_getLength(asset);
    std::vector<char> buffer(assetLength + 1, 0); // +1 for null terminator
    AAsset_read(asset, buffer.data(), assetLength);
    AAsset_close(asset);
    return buffer;
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_init(JNIEnv *env, jclass clazz, jobject asset_manager) {
    assetManager = AAssetManager_fromJava(env, asset_manager);
    auto* instance = getInstance();
    if (!instance->factory) {
        instance->factory = new dragonBones::opengl::OpenGLFactory();
    }
    if (!instance->worldClock) {
        instance->worldClock = new dragonBones::WorldClock();
    }
    LOGI("DragonBones JNI Initialized");
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_loadDragonBones(JNIEnv *env, jclass clazz, jstring model_path, jstring texture_path) {
    auto* instance = getInstance();
    if (!instance) return;

    const char* modelPathStr = env->GetStringUTFChars(model_path, nullptr);
    const char* texturePngPathStr = env->GetStringUTFChars(texture_path, nullptr);

    std::string textureJsonPathStr = texturePngPathStr;
    size_t last_dot = textureJsonPathStr.find_last_of(".");
    if (last_dot != std::string::npos) {
        textureJsonPathStr.replace(last_dot, std::string::npos, ".json");
    }

    LOGI("Buffering asset files...");
    instance->dragonBonesDataBuffer = readFileFromAssets(modelPathStr);
    instance->textureJsonBuffer = readFileFromAssets(textureJsonPathStr);
    instance->texturePngDataBuffer = readFileFromAssets(texturePngPathStr);

    if (instance->dragonBonesDataBuffer.empty() || instance->textureJsonBuffer.empty() || instance->texturePngDataBuffer.empty()) {
        LOGE("Failed to read one or more asset files into buffer.");
        instance->assetsLoaded = false;
    } else {
        LOGI("Asset files successfully buffered.");
        instance->assetsLoaded = true;
    }

    env->ReleaseStringUTFChars(model_path, modelPathStr);
    env->ReleaseStringUTFChars(texture_path, texturePngPathStr);
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onPause(JNIEnv *env, jclass clazz) {
    LOGI("DragonBones onPause");
    // 暂停动画和渲染
    if (getInstance()->worldClock) {
        // 可以在这里保存状态或暂停动画
    }
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onResume(JNIEnv *env, jclass clazz) {
    LOGI("DragonBones onResume");
    // 恢复动画和渲染
    if (getInstance()->worldClock) {
        // 可以在这里恢复之前的状态
    }
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onDestroy(JNIEnv *env, jclass clazz) {
    LOGI("DragonBones onDestroy");
    delete getInstance();
    instance = nullptr;
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onSurfaceCreated(JNIEnv *env, jclass clazz) {
    LOGI("DragonBones onSurfaceCreated");
    auto* instance = getInstance();
    if (!instance) return;

    // 1. Setup GL State
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    
    // 2. Create shader program
    instance->programId = createShaderProgram();
    if (!instance->programId) {
        LOGE("Failed to create shader program");
        return;
    }
    
    // 3. Get shader locations
    instance->positionLocation = glGetAttribLocation(instance->programId, "a_position");
    instance->texCoordLocation = glGetAttribLocation(instance->programId, "a_texCoord");
    instance->mvpMatrixLocation = glGetUniformLocation(instance->programId, "u_mvpMatrix");
    instance->textureLocation = glGetUniformLocation(instance->programId, "u_texture");
    
    if (instance->positionLocation < 0 || instance->texCoordLocation < 0 || 
        instance->mvpMatrixLocation < 0 || instance->textureLocation < 0) {
        LOGE("Failed to get one or more shader variable locations.");
        return;
    }

    // 4. Parse data and build armature if not already done
    if (!instance->assetsLoaded) {
        LOGE("Assets not loaded, skipping armature creation.");
        return;
    }
    if (instance->armature) {
        LOGI("Armature already exists at %p, skipping recreation.", instance->armature);
        return; 
    }

    LOGI("Creating armature on GL thread...");
    std::pair<void*, int> textureInfo = {instance->texturePngDataBuffer.data(), (int)instance->texturePngDataBuffer.size()};
    auto* textureAtlasData = instance->factory->parseTextureAtlasData(instance->textureJsonBuffer.data(), &textureInfo);
    if (!textureAtlasData) {
        LOGE("Failed to parse texture atlas data.");
        return;
    }
    instance->factory->addTextureAtlasData(textureAtlasData);

    auto* dragonBonesData = instance->factory->parseDragonBonesData(instance->dragonBonesDataBuffer.data());
    if (!dragonBonesData) {
        LOGE("Failed to parse DragonBones data.");
        return;
    }

    const auto& armatureNames = dragonBonesData->getArmatureNames();
    std::string armatureNameToBuild;
    if (!armatureNames.empty()) {
        bool dragonFound = false;
        for (const auto& name : armatureNames) {
            if (name == "Dragon") {
                armatureNameToBuild = name;
                dragonFound = true;
                break;
            }
        }
        if (!dragonFound) {
            armatureNameToBuild = armatureNames[0]; 
        }
    }

    if (armatureNameToBuild.empty()) {
        LOGE("No armatures found in DragonBones data.");
        return;
    }

    auto* armatureObject = instance->factory->buildArmature(armatureNameToBuild, "", "", dragonBonesData->name);
    if (armatureObject)
    {
        instance->armature = armatureObject;
        LOGI("Armature '%s' built at %p, instance is %p", armatureNameToBuild.c_str(), instance->armature, instance);
        instance->worldClock->add(armatureObject);
        if (!armatureObject->getAnimation()->getAnimationNames().empty()) {
            const auto& initialAnimation = armatureObject->getAnimation()->getAnimationNames()[0];
            LOGI("Playing initial animation: '%s'", initialAnimation.c_str());
            armatureObject->getAnimation()->play(initialAnimation);
        }
    } else {
        LOGE("Failed to build armature '%s'.", armatureNameToBuild.c_str());
    }
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onSurfaceChanged(JNIEnv *env, jclass clazz, jint width, jint height) {
    auto* instance = getInstance();
    if (instance) {
        glViewport(0, 0, width, height);
        viewportWidth = (float)width;
        viewportHeight = (float)height;
        // Create the projection matrix to map pixel coordinates to screen space
        createOrthographicMatrix(0.0f, (float)width, (float)height, 0.0f, -1.0f, 1.0f, instance->projectionMatrix);
    }
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_onDrawFrame(JNIEnv *env, jclass clazz) {
    auto* instance = getInstance();
    if (instance && instance->armature)
    {
        // 1. Clear the screen
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 2. Advance animation time
        instance->worldClock->advanceTime(1.0f / 60.0f);
        
        // 3. Setup the rendering program and global GL state
        glUseProgram(instance->programId);
        glEnableVertexAttribArray(instance->positionLocation);
        glEnableVertexAttribArray(instance->texCoordLocation);
        
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(instance->textureLocation, 0);
        
        // 4. Create a "view" matrix to scale and center the entire armature
        float viewMatrix[16], scaleM[16], transM[16];
        createScaleMatrix(scaleM, 0.5f, 0.5f, 1.0f);
        createTranslateMatrix(transM, viewportWidth / 2.0f, viewportHeight / 2.0f, 0.0f);
        multiplyMatrices(transM, scaleM, viewMatrix);
        
        // 5. Render each slot
        const auto& slots = instance->armature->getSlots();
        LOGI("onDrawFrame: Armature '%s' has %zu slots.", instance->armature->getName().c_str(), slots.size());

        int renderedSlots = 0;
        for (const auto& slot : slots) {
            if (!slot) {
                LOGW("onDrawFrame: Skipping null slot.");
                continue;
            }

            if (!slot->getVisible()) {
                LOGW("onDrawFrame: Slot '%s' is not visible.", slot->getName().c_str());
                continue;
            }

            if (!slot->getDisplay()) {
                LOGW("onDrawFrame: Slot '%s' has no display object.", slot->getName().c_str());
                continue;
            }
            
            auto* openglSlot = static_cast<dragonBones::opengl::OpenGLSlot*>(slot);
            if (!openglSlot) {
                LOGW("onDrawFrame: Slot '%s' could not be cast to OpenGLSlot.", slot->getName().c_str());
                continue;
            }

            if (openglSlot->vertices.empty() || openglSlot->indices.empty() || openglSlot->textureID == 0) {
                LOGW("onDrawFrame: Skipping slot '%s' due to empty buffers or texture ID 0 (vertices: %zu, indices: %zu, textureID: %u)",
                    slot->getName().c_str(), openglSlot->vertices.size(), openglSlot->indices.size(), openglSlot->textureID);
                continue;
            }

            // A. Get this slot's unique transformation matrix
            float slotModelMatrix[16];
            convertDBMatrixToGL(slot->globalTransformMatrix, slotModelMatrix);

            // B. Create the final MVP matrix: MVP = Projection * View * SlotModel
            float pvMatrix[16], mvpMatrix[16];
            multiplyMatrices(instance->projectionMatrix, viewMatrix, pvMatrix);
            multiplyMatrices(pvMatrix, slotModelMatrix, mvpMatrix);
            
            // C. Pass the final matrix to the shader
            glUniformMatrix4fv(instance->mvpMatrixLocation, 1, GL_FALSE, mvpMatrix);
            
            // D. Bind the texture and draw
            glBindTexture(GL_TEXTURE_2D, openglSlot->textureID);
            
            const GLsizei stride = 4 * sizeof(float);
            glVertexAttribPointer(instance->positionLocation, 2, GL_FLOAT, GL_FALSE, stride, openglSlot->vertices.data());
            glVertexAttribPointer(instance->texCoordLocation, 2, GL_FLOAT, GL_FALSE, stride, (const GLvoid*)(openglSlot->vertices.data() + 2));
            
            glDrawElements(GL_TRIANGLES, openglSlot->indices.size(), GL_UNSIGNED_SHORT, openglSlot->indices.data());
            renderedSlots++;
        }
        
        if (renderedSlots == 0 && !slots.empty()) {
            LOGW("onDrawFrame: Rendered 0 slots out of %zu.", slots.size());
        }

        // 6. Cleanup
        glDisableVertexAttribArray(instance->positionLocation);
        glDisableVertexAttribArray(instance->texCoordLocation);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_dragonbones_JniBridge_destroy(JNIEnv *env, jobject thiz) {
    // ... existing code ...
} 