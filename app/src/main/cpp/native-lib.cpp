#include <jni.h>
#include <string>
#include <strings.h>
#include <android/log.h>
#include <thread>
#include <mutex>
#include <openconnect.h>
#include <unistd.h>
#include <stdarg.h>
#include <cstring>
#include <cctype>

#define LOG_TAG "HybridNestJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global JNI Cache
static jclass g_vpnStateManagerClass = nullptr;
static jmethodID g_updateStateMethodID = nullptr;
static jmethodID g_updateStateForSessionMethodID = nullptr;

static JavaVM *g_vm = nullptr;

struct vpn_params {
    std::string server;
    std::string user;
    std::string pass;
    int tun_fd;
    jlong session_id;
    jobject service_obj;
};

struct vpn_state {
    std::mutex mtx;
    struct openconnect_info *vpninfo;
    int cancel_fd;
    bool should_stop;
    std::string user;
    std::string pass;
    jobject service_obj;
};

static vpn_state active_vpn;

// Helper for case-insensitive comparison
static bool strcasecmp_equal(const char *s1, const char *s2) {
    if (!s1 || !s2) return false;
    return strcasecmp(s1, s2) == 0;
}

// JNI State codes: 0=DISCONNECTED, 1=CONNECTING, 2=CONNECTED, 3=ERROR
void update_kotlin_state(JNIEnv *env, int state_code, jlong session_id = 0L) {
    if (!env || !g_vpnStateManagerClass) return;
    if (g_updateStateForSessionMethodID && session_id != 0L) {
        env->CallStaticVoidMethod(g_vpnStateManagerClass, g_updateStateForSessionMethodID, state_code, session_id);
    } else if (g_updateStateMethodID) {
        env->CallStaticVoidMethod(g_vpnStateManagerClass, g_updateStateMethodID, state_code);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

// Callback for handling certificate validation
static int on_validate_peer_cert(void *privdata, const char *reason) {
    LOGI("Peer certificate validation (automatically accepting): %s", reason ? reason : "unknown");
    return 0; // 0 = accept
}

static void on_protect_socket(void *privdata, int fd) {
    vpn_state *state = (vpn_state *) privdata;
    if (!state) return;

    jobject service_obj = nullptr;
    {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (!state->service_obj) return;
        service_obj = state->service_obj;
    }

    JNIEnv *env;
    bool attached = false;
    jint res = g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach thread for protectSocket");
            return;
        }
        attached = true;
    } else if (res != JNI_OK) {
        LOGE("Failed to get JNIEnv for protectSocket");
        return;
    }

    jclass svc_cls = env->GetObjectClass(service_obj);
    if (svc_cls) {
        jmethodID mid = env->GetMethodID(svc_cls, "protectSocket", "(I)V");
        if (mid) {
            env->CallVoidMethod(service_obj, mid, fd);
            if (env->ExceptionCheck()) {
                LOGE("Exception in protectSocket");
                env->ExceptionClear();
            }
        } else {
            LOGE("Failed to find protectSocket method");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
        env->DeleteLocalRef(svc_cls);
    }

    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

// Callback for handling new config XML
static int on_write_new_config(void *privdata, const char *buf, int buflen) {
    LOGI("Received new XML configuration (%d bytes)", buflen);
    return 0;
}

// Callback for handling authentication forms
static int on_process_auth_form(void *privdata, struct oc_auth_form *form) {
    try {
        vpn_state *state = (vpn_state *) privdata;
        if (!state || !form) return 0;

        struct oc_form_opt *opt;

        LOGI("Processing auth form: %s (%s)", form->banner ? form->banner : "",
             form->message ? form->message : "");

        std::string user, pass;
        {
            std::lock_guard<std::mutex> lock(state->mtx);
            user = state->user;
            pass = state->pass;
        }

        for (opt = form->opts; opt; opt = opt->next) {
            if (!opt || !opt->name) continue;

            if (opt->type == OC_FORM_OPT_TEXT &&
                (strcasecmp_equal(opt->name, "username") ||
                 strcasecmp_equal(opt->name, "user") ||
                 strcasecmp_equal(opt->name, "login") ||
                 strcasecmp_equal(opt->name, "auth_user"))) {
                LOGI("Injecting username into option: %s", opt->name);
                openconnect_set_option_value(opt, user.c_str());
            } else if (opt->type == OC_FORM_OPT_PASSWORD &&
                       (strcasecmp_equal(opt->name, "password") ||
                        strcasecmp_equal(opt->name, "pass") ||
                        strcasecmp_equal(opt->name, "auth_pass"))) {
                LOGI("Injecting password into option: %s", opt->name);
                openconnect_set_option_value(opt, pass.c_str());
            }
        }
    } catch (const std::exception &e) {
        LOGE("Exception in on_process_auth_form: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception in on_process_auth_form");
    }
    return 0;
}

// ---------------------------------------------------------------------------
// Producer-side log redaction.
// The 2026-10-07 device logcat captured REAL Set-Cookie values because OpenConnect
// progress output was forwarded verbatim. Redact secret-bearing values HERE, before
// anything reaches logcat. Header/token NAMES are preserved for debuggability.
// ---------------------------------------------------------------------------

static bool iequals(const char *a, const char *b) {
    if (!a || !b) return false;
    while (*a && *b) {
        if (tolower((unsigned char) *a) != tolower((unsigned char) *b)) return false;
        a++; b++;
    }
    return *a == '\0' && *b == '\0';
}

// Secret-bearing HTTP header names (case-insensitive). Values are never logged.
static const char *const SECRET_HEADERS[] = {
    "Set-Cookie", "Cookie", "Authorization", "Proxy-Authorization"
};

static void redact_secrets_in_place(char *line) {
    if (!line) return;
    for (const char *name : SECRET_HEADERS) {
        size_t name_len = strlen(name);
        char *p = line;
        while ((p = strcasestr(p, name)) != nullptr) {
            // Boundary: start-of-line or the OpenConnect progress prefix "[n]: ".
            bool boundary = (p == line) ||
                            (*(p - 1) == '\n') ||
                            (p >= line + 2 && *(p - 1) == ' ' && (*(p - 2) == ':' || *(p - 2) == ']'));
            if (!boundary) { p += name_len; continue; }

            char *v = p + name_len;
            if (*v != ':') { p += name_len; continue; }
            v++;                 // skip ':'
            if (*v == ' ') v++;  // skip single space
            // Redact to end of line.
            while (*v && *v != '\n' && *v != '\r') { *v++ = '*'; }
            p = v;
        }
    }

    // VPN session tokens: webvpn...=<value> -> webvpn...=*
    char *q = line;
    while ((q = strcasestr(q, "webvpn")) != nullptr) {
        char *eq = q + 6;
        while (*eq && *eq != '=' && *eq != ' ' && *eq != ';') eq++;
        if (*eq == '=') {
            char *v = eq + 1;
            while (*v && *v != ';' && *v != ' ' && *v != '\n') { *v++ = '*'; }
            q = v;
        } else {
            q = eq;
        }
    }
}

static void on_progress(void *privdata, int level, const char *fmt, ...) {
    va_list args;
    char buf[1024];
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    redact_secrets_in_place(buf);
    LOGI("OpenConnect [%d]: %s", level, buf);
}

void vpn_thread_func(vpn_params params) {
    JNIEnv *env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
        LOGE("Failed to attach VPN thread to JVM");
        return;
    }

    LOGI("VPN background thread started for server: %s", params.server.c_str());

    {
        std::lock_guard<std::mutex> lock(active_vpn.mtx);
        active_vpn.user = params.user;
        active_vpn.pass = params.pass;
        active_vpn.should_stop = false;
        active_vpn.cancel_fd = -1;
        active_vpn.service_obj = params.service_obj;
        active_vpn.vpninfo = nullptr;
    }

    LOGI("Initializing OpenConnect session...");
    struct openconnect_info *vpninfo = openconnect_vpninfo_new(
            "HybridNest-Android",
            on_validate_peer_cert,
            on_write_new_config,
            on_process_auth_form,
            on_progress,
            &active_vpn
    );

    if (!vpninfo) {
        LOGE("Failed to initialize openconnect_info");
        update_kotlin_state(env, 3, params.session_id); // ERROR
        goto thread_cleanup;
    }

    openconnect_set_protect_socket_handler(vpninfo, on_protect_socket);

    {
        std::lock_guard<std::mutex> lock(active_vpn.mtx);
        active_vpn.vpninfo = vpninfo;
        active_vpn.cancel_fd = openconnect_setup_cmd_pipe(vpninfo);
    }

    // Set hostname/URL
    if (openconnect_parse_url(vpninfo, params.server.c_str())) {
        LOGE("Failed to parse server URL: %s", params.server.c_str());
        update_kotlin_state(env, 3, params.session_id); // ERROR
        goto cleanup;
    }

    update_kotlin_state(env, 1, params.session_id); // CONNECTING
    LOGI("Authenticating...");
    if (openconnect_obtain_cookie(vpninfo)) {
        LOGE("Failed to obtain cookie (Authentication failed)");
        update_kotlin_state(env, 3, params.session_id); // ERROR
        goto cleanup;
    }
    LOGI("Cookie obtained successfully");

    LOGI("Establishing CSTP connection...");
    if (openconnect_make_cstp_connection(vpninfo)) {
        LOGE("Failed to make CSTP connection");
        update_kotlin_state(env, 3, params.session_id); // ERROR
        goto cleanup;
    }
    LOGI("CSTP connection established");

    // Attempt DTLS
    openconnect_setup_dtls(vpninfo, 60);

    // Dynamic TUN Configuration
    {
        const struct oc_ip_info *ip_info;
        if (openconnect_get_ip_info(vpninfo, &ip_info, nullptr, nullptr) == 0 && ip_info) {
            const char *assigned_ip = ip_info->addr;
            if (!assigned_ip) {
                LOGE("Assigned IP address is NULL");
                update_kotlin_state(env, 3, params.session_id); // ERROR
                goto cleanup;
            }
            // NEW (Stage 1 corrective): forward ALL server-assigned DNS servers.
            // Previously only dns[0] reached Kotlin, so e.g. the server's second
            // advertised resolver (X-CSTP-DNS: 8.8.4.4 in the device log) never
            // reached the VpnService.Builder. Comma-joined, preserving order.
            // `ip_info->dns` is a fixed array `const char *dns[3]` (not a nullable
            // pointer); iterate by index, bounded to the array size.
            std::string dns_list;
            for (int i = 0; i < 3; ++i) {
                if (ip_info->dns[i] == nullptr) continue;
                if (!dns_list.empty()) dns_list += ",";
                dns_list += ip_info->dns[i];
            }
            LOGI("Server assigned IP: %s, DNS: %s", assigned_ip,
                 dns_list.empty() ? "(none)" : dns_list.c_str());

            jstring j_ip = env->NewStringUTF(assigned_ip);
            jstring j_dns = env->NewStringUTF(dns_list.c_str());

            jclass svc_cls = env->GetObjectClass(params.service_obj);
            if (svc_cls) {
                jmethodID mid = env->GetMethodID(svc_cls, "establishTunNative",
                                                 "(Ljava/lang/String;Ljava/lang/String;)I");

                if (mid) {
                    int new_fd = env->CallIntMethod(params.service_obj, mid, j_ip, j_dns);
                    if (env->ExceptionCheck()) {
                        env->ExceptionDescribe();
                        env->ExceptionClear();
                        LOGE("Exception in establishTunNative");
                        env->DeleteLocalRef(j_ip);
                        env->DeleteLocalRef(j_dns);
                        env->DeleteLocalRef(svc_cls);
                        update_kotlin_state(env, 3, params.session_id); // ERROR
                        goto cleanup;
                    }
                    if (new_fd != -1) {
                        LOGI("Received new FD from Kotlin: %d", new_fd);
                        openconnect_setup_tun_fd(vpninfo, new_fd);
                    } else {
                        LOGE("Failed to establish TUN via Kotlin callback");
                        env->DeleteLocalRef(j_ip);
                        env->DeleteLocalRef(j_dns);
                        env->DeleteLocalRef(svc_cls);
                        update_kotlin_state(env, 3, params.session_id); // ERROR
                        goto cleanup;
                    }
                } else {
                    LOGE("Failed to find establishTunNative method");
                    env->DeleteLocalRef(j_ip);
                    env->DeleteLocalRef(j_dns);
                    env->DeleteLocalRef(svc_cls);
                    update_kotlin_state(env, 3, params.session_id); // ERROR
                    goto cleanup;
                }
                env->DeleteLocalRef(svc_cls);
            }
            env->DeleteLocalRef(j_ip);
            env->DeleteLocalRef(j_dns);
        } else {
            LOGE("Failed to get IP info from OpenConnect");
            update_kotlin_state(env, 3, params.session_id); // ERROR
            goto cleanup;
        }
    }

    update_kotlin_state(env, 2, params.session_id); // CONNECTED
    LOGI("Entering OpenConnect main loop...");
    {
        bool stop_req = false;
        {
            std::lock_guard<std::mutex> lock(active_vpn.mtx);
            stop_req = active_vpn.should_stop;
        }
        if (!stop_req) {
            int ret = openconnect_mainloop(vpninfo, 300, 10);
            if (ret < 0) {
                LOGE("Main loop encountered an error: %d", ret);
                update_kotlin_state(env, 3, params.session_id); // ERROR
            } else {
                LOGI("Main loop exited normally");
                update_kotlin_state(env, 0, params.session_id); // DISCONNECTED
            }
        } else {
            LOGI("Main loop skipped due to stop request");
            update_kotlin_state(env, 0, params.session_id); // DISCONNECTED
        }
    }

cleanup:
    LOGI("Cleaning up OpenConnect session...");
    {
        std::lock_guard<std::mutex> lock(active_vpn.mtx);
        if (active_vpn.vpninfo) {
            openconnect_vpninfo_free(active_vpn.vpninfo);
            active_vpn.vpninfo = nullptr;
        }
        active_vpn.cancel_fd = -1;
    }

thread_cleanup:
    {
        std::lock_guard<std::mutex> lock(active_vpn.mtx);
        if (active_vpn.service_obj) {
            env->DeleteGlobalRef(active_vpn.service_obj);
            active_vpn.service_obj = nullptr;
        }
    }
    LOGI("VPN background thread finished");
    g_vm->DetachCurrentThread();
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    // Globally cache the State Manager to eliminate FindClass overhead during runtime
    jclass localClass = env->FindClass("dev/rouzbeh/hybridvpn/service/VpnStateManager");
    if (localClass) {
        g_vpnStateManagerClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        g_updateStateMethodID = env->GetStaticMethodID(g_vpnStateManagerClass,
                                                       "updateStateFromNative", "(I)V");
        g_updateStateForSessionMethodID = env->GetStaticMethodID(g_vpnStateManagerClass,
                                                             "updateStateFromNativeForSession", "(IJ)V");
    }
    return JNI_VERSION_1_6;
}

static std::thread g_vpn_thread;
static std::mutex g_thread_mtx;

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_startOpenConnectNative(
        JNIEnv *env,
        jobject thiz,
        jstring server_address,
        jstring username,
        jstring password,
        jint vpn_file_descriptor,
        jlong session_id) {

    const char *native_server = env->GetStringUTFChars(server_address, nullptr);
    const char *native_user = env->GetStringUTFChars(username, nullptr);
    const char *native_pass = env->GetStringUTFChars(password, nullptr);

    LOGI("JNI: startOpenConnectNative called (sessionId=%lld)", (long long)session_id);

    vpn_params params;
    params.server = native_server ? native_server : "";
    params.user = native_user ? native_user : "";
    params.pass = native_pass ? native_pass : "";
    params.tun_fd = vpn_file_descriptor;
    params.session_id = session_id;
    params.service_obj = env->NewGlobalRef(thiz);

    {
        std::lock_guard<std::mutex> lock(g_thread_mtx);
        if (g_vpn_thread.joinable()) {
            LOGI("JNI: Joining previous native OpenConnect thread...");
            g_vpn_thread.join();
            LOGI("JNI: Previous native thread joined");
        }
        g_vpn_thread = std::thread(vpn_thread_func, params);
    }

    if (native_server) env->ReleaseStringUTFChars(server_address, native_server);
    if (native_user) env->ReleaseStringUTFChars(username, native_user);
    if (native_pass) env->ReleaseStringUTFChars(password, native_pass);
}

static void perform_stop_openconnect() {
    LOGI("JNI: perform_stop_openconnect requested");
    {
        std::lock_guard<std::mutex> lock(active_vpn.mtx);
        active_vpn.should_stop = true;
        if (active_vpn.cancel_fd != -1) {
            LOGI("Sending cancel signal to OpenConnect pipe");
            write(active_vpn.cancel_fd, "x", 1);
        }
    }
    std::lock_guard<std::mutex> lock(g_thread_mtx);
    if (g_vpn_thread.joinable()) {
        LOGI("JNI: Joining native OpenConnect thread...");
        g_vpn_thread.join();
        LOGI("JNI: Native OpenConnect thread joined successfully");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_stopOpenConnectNative(JNIEnv *env,
                                                                          jobject thiz) {
    perform_stop_openconnect();
}

// --- OPENVPN NATIVE IMPLEMENTATION ---

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_startOpenVPNNative(
        JNIEnv *env,
        jobject thiz,
        jstring config,
        jstring username,
        jstring password) {

    const char *native_config = env->GetStringUTFChars(config, nullptr);
    const char *native_user = env->GetStringUTFChars(username, nullptr);
    const char *native_pass = env->GetStringUTFChars(password, nullptr);

    LOGI("JNI: startOpenVPNNative called");
    if (native_config) {
        LOGI("Config length: %zu", strlen(native_config));
    }

    struct ovpn_params {
        std::string config;
        jobject service_obj;
    };

    ovpn_params *params = new ovpn_params();
    params->config = native_config ? native_config : "";
    params->service_obj = env->NewGlobalRef(thiz);

    std::thread ovpn_thread([params]() {
        LOGI("OpenVPN background thread started");

        JNIEnv *thread_env;
        if (g_vm->AttachCurrentThread(&thread_env, nullptr) != JNI_OK) {
            LOGE("Failed to attach OpenVPN thread");
            delete params;
            return;
        }

        std::this_thread::sleep_for(std::chrono::seconds(2));

        jclass svc_cls = thread_env->GetObjectClass(params->service_obj);
        if (svc_cls) {
            jmethodID mid = thread_env->GetMethodID(svc_cls, "establishTunNative",
                                                    "(Ljava/lang/String;Ljava/lang/String;)I");

            if (mid) {
                jstring j_ip = thread_env->NewStringUTF("10.8.0.2");
                jstring j_dns = thread_env->NewStringUTF("8.8.8.8");
                int fd = thread_env->CallIntMethod(params->service_obj, mid, j_ip, j_dns);
                LOGI("OpenVPN: TUN established with FD %d", fd);

                update_kotlin_state(thread_env, 2); // CONNECTED
                thread_env->DeleteLocalRef(j_ip);
                thread_env->DeleteLocalRef(j_dns);
            }
            thread_env->DeleteLocalRef(svc_cls);
        }

        thread_env->DeleteGlobalRef(params->service_obj);
        delete params;
        g_vm->DetachCurrentThread();
    });
    ovpn_thread.detach();

    if (native_config) env->ReleaseStringUTFChars(config, native_config);
    if (native_user) env->ReleaseStringUTFChars(username, native_user);
    if (native_pass) env->ReleaseStringUTFChars(password, native_pass);
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_stopOpenVPNNative(JNIEnv *env, jobject thiz) {
    LOGI("JNI: stopOpenVPNNative requested");
    // Signal OpenVPN core to stop
    update_kotlin_state(env, 0); // DISCONNECTED
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_dev_rouzbeh_hybridvpn_core_vpn_OpenVPNManager_startTunnel(
        JNIEnv *env,
        jobject thiz,
        jstring config,
        jstring username,
        jstring password) {
    LOGI("JNI: OpenVPNManager_startTunnel called");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_core_vpn_OpenVPNManager_stopTunnel(JNIEnv *env, jobject thiz) {
    LOGI("JNI: OpenVPNManager_stopTunnel called");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_dev_rouzbeh_hybridvpn_core_vpn_OpenConnectManager_startTunnel(
        JNIEnv *env,
        jobject thiz,
        jint fd,
        jstring server_address,
        jstring username,
        jstring password) {
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_core_vpn_OpenConnectManager_stopTunnel(JNIEnv *env, jobject thiz) {
    LOGI("JNI: stopTunnel requested via OpenConnectManager");
    perform_stop_openconnect();
}
