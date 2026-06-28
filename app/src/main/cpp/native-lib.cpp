#include <jni.h>
#include <android/log.h>
#include <stdint.h>
#include <vector>

#define TAG "RPLIDAR_PARSER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

enum Mode {
    WAIT_FOR_DESCRIPTOR,
    PARSING_SCAN_POINTS
};

Mode g_current_mode = WAIT_FOR_DESCRIPTOR;
uint8_t g_desc_buffer[7];
int g_desc_idx = 0;

uint8_t g_packet[5];
int g_packet_idx = 0;

// Dynamic vectors to cache scan frames before passing them up
std::vector<float> g_frame_angles;
std::vector<float> g_frame_distances;

// Caches Kotlin callback identifiers to keep execution fast
jmethodID g_callback_method_id = nullptr;

void upload_frame_to_kotlin(JNIEnv *env, jobject thiz) {
    if (g_frame_angles.empty()) return;

    //Lazy-load the method ID if we haven't found it yet
    if (g_callback_method_id == nullptr) {
        jclass clazz = env->GetObjectClass(thiz);
        // Signature "([F[F)V" means: takes two float arrays, returns void
        g_callback_method_id = env->GetMethodID(clazz, "onFrameReceived", "([F[F)V");
    }

    if (g_callback_method_id == nullptr) {
        LOGW("Failed to locate Kotlin onFrameReceived method.");
        return;
    }

    //Allocate temporary JNI float arrays
    jsize size = static_cast<jsize>(g_frame_angles.size());
    jfloatArray j_angles = env->NewFloatArray(size);
    jfloatArray j_distances = env->NewFloatArray(size);

    //Copy our native vector contents straight into JNI memory spaces
    env->SetFloatArrayRegion(j_angles, 0, size, g_frame_angles.data());
    env->SetFloatArrayRegion(j_distances, 0, size, g_frame_distances.data());

    //Trigger the live Kotlin method callback!
    env->CallVoidMethod(thiz, g_callback_method_id, j_angles, j_distances);

    //Clean local native memory references to prevent local ref overflow leaks
    env->DeleteLocalRef(j_angles);
    env->DeleteLocalRef(j_distances);

    //Clear native buffers for the next frame
    g_frame_angles.clear();
    g_frame_distances.clear();
}

void parse_single_byte(JNIEnv *env, jobject thiz, uint8_t b) {
    if (g_current_mode == WAIT_FOR_DESCRIPTOR) {
        if (g_desc_idx == 0 && b != 0xA5) return;
        if (g_desc_idx == 1 && b != 0x5A) {
            g_desc_idx = 0;
            return;
        }
        g_desc_buffer[g_desc_idx++] = b;

        if (g_desc_idx == 7) {
            LOGW("Found RPLIDAR Descriptor Header! Switching to Scan Mode.");
            g_current_mode = PARSING_SCAN_POINTS;
            g_packet_idx = 0;
        }
    }
    else if (g_current_mode == PARSING_SCAN_POINTS) {
        if (g_packet_idx == 0) {
            uint8_t sync = b & 0x03;
            if (sync == 0x01 || sync == 0x02 || sync == 0x00) {
                g_packet[0] = b;
                g_packet_idx = 1;
            }
            return;
        }

        if (g_packet_idx == 1) {
            if (b & 0x01) {
                g_packet[1] = b;
                g_packet_idx = 2;
            } else {
                g_packet_idx = 0;
            }
            return;
        }

        g_packet[g_packet_idx++] = b;

        if (g_packet_idx == 5) {
            uint8_t quality = g_packet[0] >> 2;
            bool start_bit = (g_packet[0] & 0x01);

            uint16_t angle_q6 = (g_packet[2] << 7) | (g_packet[1] >> 1);
            float angle_deg = (float)angle_q6 / 64.0f;

            uint16_t distance_raw = (g_packet[4] << 8) | g_packet[3];
            float distance_mm = (float)distance_raw / 4.0f;

            // If a brand new revolution starts, ship the current frame up to Kotlin first
            if (start_bit) {
                upload_frame_to_kotlin(env, thiz);
            }

            if (distance_mm > 0) {
                g_frame_angles.push_back(angle_deg);
                g_frame_distances.push_back(distance_mm);
            }

            g_packet_idx = 0;
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_za_co_grab_rplidar_MainActivity_processRawBytes(JNIEnv *env, jobject thiz, jbyteArray data_array) {
    jbyte *bytes = env->GetByteArrayElements(data_array, nullptr);
    jsize length = env->GetArrayLength(data_array);

    for (int i = 0; i < length; i++) {
        // Pass env and thiz context pointers down so parser can call up to JVM when needed
        parse_single_byte(env, thiz, (uint8_t)bytes[i]);
    }

    env->ReleaseByteArrayElements(data_array, bytes, JNI_ABORT);
}