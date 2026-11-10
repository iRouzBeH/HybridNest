#include <jni.h>
#include <string>
#include <strings.h>
#include <android/log.h>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <vector>
#include <memory>
#include <algorithm>
#include <chrono>
#include <sys/socket.h>
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

struct vpn_state {
    std::mutex mtx;
    std::condition_variable cv;
    struct openconnect_info *vpninfo = nullptr;
    int cancel_fd = -1;
    int tun_fd = -1;
    bool should_stop = false;
    bool finished = false;
    jlong session_id = 0L;
    std::string server;
    std::string user;
    std::string pass;
    jobject service_obj = nullptr;
    std::vector<int> protected_fds;
};

static std::mutex g_state_mtx;
static std::shared_ptr<vpn_state> g_active_vpn;
static std::thread g_vpn_thread;

// Helper for case-insensitive comparison
static bool strcasecmp_equal(const char *s1, const char *s2) {
    if (!s1 || !s2) return false;
    return strcasecmp(s1, s2) == 0;
}

static void shutdown_tracked_socket(int fd) {
    if (fd < 0) return;
    int sock_type = 0;
    socklen_t optlen = sizeof(sock_type);
    if (::getsockopt(fd, SOL_SOCKET, SO_TYPE, &sock_type, &optlen) == 0) {
        int r = ::shutdown(fd, SHUT_RDWR);
        LOGI("Force-shutdown OpenConnect socket fd=%d -> %d", fd, r);
    }
}

static void signal_stop_on_state(const std::shared_ptr<vpn_state> &state) {
    if (!state) return;
    std::lock_guard<std::mutex> lock(state->mtx);
    state->should_stop = true;
    if (state->cancel_fd != -1) {
        LOGI("Sending cancel signal to OpenConnect cmd pipe (fd=%d, session=%lld)",
             state->cancel_fd, (long long) state->session_id);
        ssize_t ignored = ::write(state->cancel_fd, "x", 1);
        (void) ignored;
    }
    for (int fd : state->protected_fds) {
        if (fd >= 0 && fd != state->cancel_fd && fd != state->tun_fd) {
            shutdown_tracked_socket(fd);
        }
    }
    state->protected_fds.clear();
}

static bool is_stop_requested(const std::shared_ptr<vpn_state> &state) {
    if (!state) return true;
    std::lock_guard<std::mutex> lock(state->mtx);
    return state->should_stop;
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
    vpn_state *state = static_cast<vpn_state *>(privdata);
    if (state) {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (state->should_stop) {
            LOGI("Peer certificate validation aborted due to stop request");
            return -1;
        }
    }
    LOGI("Peer certificate validation (automatically accepting): %s", reason ? reason : "unknown");
    return 0; // 0 = accept
}

static void on_protect_socket(void *privdata, int fd) {
    vpn_state *state = static_cast<vpn_state *>(privdata);
    if (!state) return;

    jobject service_obj = nullptr;
    {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (state->should_stop) {
            LOGI("on_protect_socket(fd=%d) called after stop requested; shutting down immediately", fd);
            shutdown_tracked_socket(fd);
            return;
        }
        if (fd >= 0 &&
            std::find(state->protected_fds.begin(), state->protected_fds.end(), fd) ==
            state->protected_fds.end()) {
            state->protected_fds.push_back(fd);
        }
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

    {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (state->should_stop) {
            LOGI("Stop requested during protectSocket(fd=%d); shutting down immediately", fd);
            shutdown_tracked_socket(fd);
        }
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
        vpn_state *state = static_cast<vpn_state *>(privdata);
        if (!state || !form) return -1;

        struct oc_form_opt *opt;

        LOGI("Processing auth form: %s (%s)", form->banner ? form->banner : "",
             form->message ? form->message : "");

        std::string user, pass;
        {
            std::lock_guard<std::mutex> lock(state->mtx);
            if (state->should_stop) {
                LOGI("Auth form cancelled due to stop request");
                return -1;
            }
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

void vpn_thread_func(std::shared_ptr<vpn_state> state) {
    JNIEnv *env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
        LOGE("Failed to attach VPN thread to JVM");
        std::lock_guard<std::mutex> lock(state->mtx);
        state->finished = true;
        state->cv.notify_all();
        return;
    }

    const jlong session_id = state->session_id;
    struct openconnect_info *vpninfo = nullptr;
    LOGI("VPN background thread started for server: %s (session=%lld)",
         state->server.c_str(), (long long) session_id);

    if (is_stop_requested(state)) {
        LOGI("Stop already requested before OpenConnect init (session=%lld)", (long long) session_id);
        goto thread_cleanup;
    }

    LOGI("Initializing OpenConnect session...");
    vpninfo = openconnect_vpninfo_new(
            "HybridNest-Android",
            on_validate_peer_cert,
            on_write_new_config,
            on_process_auth_form,
            on_progress,
            state.get()
    );

    if (!vpninfo) {
        LOGE("Failed to initialize openconnect_info");
        if (!is_stop_requested(state)) {
            update_kotlin_state(env, 3, session_id); // ERROR
        }
        goto thread_cleanup;
    }

    openconnect_set_protect_socket_handler(vpninfo, on_protect_socket);

    {
        std::lock_guard<std::mutex> lock(state->mtx);
        state->vpninfo = vpninfo;
        state->cancel_fd = openconnect_setup_cmd_pipe(vpninfo);
        if (state->should_stop && state->cancel_fd != -1) {
            ssize_t ignored = ::write(state->cancel_fd, "x", 1);
            (void) ignored;
        }
    }

    if (is_stop_requested(state)) {
        LOGI("Stop requested right after cmd pipe setup (session=%lld)", (long long) session_id);
        goto cleanup;
    }

    // Set hostname/URL
    if (openconnect_parse_url(vpninfo, state->server.c_str())) {
        LOGE("Failed to parse server URL: %s", state->server.c_str());
        if (!is_stop_requested(state)) {
            update_kotlin_state(env, 3, session_id); // ERROR
        }
        goto cleanup;
    }

    update_kotlin_state(env, 1, session_id); // CONNECTING
    LOGI("Authenticating...");
    if (openconnect_obtain_cookie(vpninfo)) {
        if (is_stop_requested(state)) {
            LOGI("openconnect_obtain_cookie interrupted by stop/cancel (session=%lld)", (long long) session_id);
        } else {
            LOGE("Failed to obtain cookie (Authentication failed)");
            update_kotlin_state(env, 3, session_id); // ERROR
        }
        goto cleanup;
    }
    if (is_stop_requested(state)) {
        LOGI("Stop requested after cookie obtained; aborting before CSTP (session=%lld)", (long long) session_id);
        goto cleanup;
    }
    LOGI("Cookie obtained successfully");

    LOGI("Establishing CSTP connection...");
    if (openconnect_make_cstp_connection(vpninfo)) {
        if (is_stop_requested(state)) {
            LOGI("openconnect_make_cstp_connection interrupted by stop/cancel (session=%lld)", (long long) session_id);
        } else {
            LOGE("Failed to make CSTP connection");
            update_kotlin_state(env, 3, session_id); // ERROR
        }
        goto cleanup;
    }
    if (is_stop_requested(state)) {
        LOGI("Stop requested after CSTP connection; aborting before TUN setup (session=%lld)", (long long) session_id);
        goto cleanup;
    }
    LOGI("CSTP connection established");

    // Attempt DTLS
    openconnect_setup_dtls(vpninfo, 60);

    if (is_stop_requested(state)) {
        LOGI("Stop requested after DTLS setup; aborting before TUN establish (session=%lld)", (long long) session_id);
        goto cleanup;
    }

    // Dynamic TUN Configuration
    {
        const struct oc_ip_info *ip_info;
        if (openconnect_get_ip_info(vpninfo, &ip_info, nullptr, nullptr) == 0 && ip_info) {
            const char *assigned_ip = ip_info->addr;
            if (!assigned_ip) {
                LOGE("Assigned IP address is NULL");
                if (!is_stop_requested(state)) {
                    update_kotlin_state(env, 3, session_id); // ERROR
                }
                goto cleanup;
            }
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

            jobject service_obj = nullptr;
            {
                std::lock_guard<std::mutex> lock(state->mtx);
                service_obj = state->service_obj;
            }

            jclass svc_cls = service_obj ? env->GetObjectClass(service_obj) : nullptr;
            if (svc_cls) {
                jmethodID mid = env->GetMethodID(svc_cls, "establishTunNative",
                                                 "(Ljava/lang/String;Ljava/lang/String;)I");

                if (mid) {
                    int new_fd = env->CallIntMethod(service_obj, mid, j_ip, j_dns);
                    if (env->ExceptionCheck()) {
                        env->ExceptionDescribe();
                        env->ExceptionClear();
                        LOGE("Exception in establishTunNative");
                        env->DeleteLocalRef(j_ip);
                        env->DeleteLocalRef(j_dns);
                        env->DeleteLocalRef(svc_cls);
                        if (!is_stop_requested(state)) {
                            update_kotlin_state(env, 3, session_id); // ERROR
                        }
                        goto cleanup;
                    }
                    if (new_fd != -1) {
                        LOGI("Received new FD from Kotlin: %d", new_fd);
                        {
                            std::lock_guard<std::mutex> lock(state->mtx);
                            state->tun_fd = new_fd;
                        }
                        openconnect_setup_tun_fd(vpninfo, new_fd);
                    } else {
                        LOGE("Failed to establish TUN via Kotlin callback");
                        env->DeleteLocalRef(j_ip);
                        env->DeleteLocalRef(j_dns);
                        env->DeleteLocalRef(svc_cls);
                        if (!is_stop_requested(state)) {
                            update_kotlin_state(env, 3, session_id); // ERROR
                        }
                        goto cleanup;
                    }
                } else {
                    LOGE("Failed to find establishTunNative method");
                    env->DeleteLocalRef(j_ip);
                    env->DeleteLocalRef(j_dns);
                    env->DeleteLocalRef(svc_cls);
                    if (!is_stop_requested(state)) {
                        update_kotlin_state(env, 3, session_id); // ERROR
                    }
                    goto cleanup;
                }
                env->DeleteLocalRef(svc_cls);
            }
            env->DeleteLocalRef(j_ip);
            env->DeleteLocalRef(j_dns);
        } else {
            LOGE("Failed to get IP info from OpenConnect");
            if (!is_stop_requested(state)) {
                update_kotlin_state(env, 3, session_id); // ERROR
            }
            goto cleanup;
        }
    }

    if (is_stop_requested(state)) {
        LOGI("Stop requested right before CONNECTED; skipping mainloop (session=%lld)", (long long) session_id);
        goto cleanup;
    }

    update_kotlin_state(env, 2, session_id); // CONNECTED
    LOGI("Entering OpenConnect main loop...");
    {
        if (!is_stop_requested(state)) {
            int ret = openconnect_mainloop(vpninfo, 300, 10);
            if (is_stop_requested(state)) {
                // Intentional disconnect/cancel: Kotlin's stopAllEngines owns the
                // DISCONNECTING/CANCELING -> DISCONNECTED transition and its timing.
                LOGI("Main loop exited due to intentional stop (ret=%d, session=%lld)", ret, (long long) session_id);
            } else if (ret < 0) {
                LOGE("Main loop encountered an error: %d", ret);
                update_kotlin_state(env, 3, session_id); // ERROR
            } else {
                LOGI("Main loop exited normally");
                update_kotlin_state(env, 0, session_id); // DISCONNECTED
            }
        } else {
            LOGI("Main loop skipped due to stop request");
        }
    }

cleanup:
    LOGI("Cleaning up OpenConnect session (session=%lld)...", (long long) session_id);
    {
        std::lock_guard<std::mutex> lock(state->mtx);
        state->protected_fds.clear();
        state->cancel_fd = -1;
        if (state->vpninfo) {
            openconnect_vpninfo_free(state->vpninfo);
            state->vpninfo = nullptr;
        }
    }

thread_cleanup:
    {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (state->service_obj) {
            env->DeleteGlobalRef(state->service_obj);
            state->service_obj = nullptr;
        }
        state->finished = true;
        state->cv.notify_all();
    }
    LOGI("VPN background thread finished (session=%lld)", (long long) session_id);
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

static void interrupt_active_openconnect() {
    std::shared_ptr<vpn_state> current;
    {
        std::lock_guard<std::mutex> lock(g_state_mtx);
        current = g_active_vpn;
    }
    if (current) {
        LOGI("JNI: interrupt_active_openconnect signaling session %lld", (long long) current->session_id);
        signal_stop_on_state(current);
    }
}

static void perform_stop_openconnect() {
    LOGI("JNI: perform_stop_openconnect requested");
    std::shared_ptr<vpn_state> current;
    {
        std::lock_guard<std::mutex> lock(g_state_mtx);
        current = g_active_vpn;
    }
    if (current) {
        signal_stop_on_state(current);
        std::unique_lock<std::mutex> state_lock(current->mtx);
        bool exited = current->cv.wait_for(
                state_lock,
                std::chrono::milliseconds(1500),
                [&current] { return current->finished; }
        );
        state_lock.unlock();

        std::lock_guard<std::mutex> lock(g_state_mtx);
        if (g_vpn_thread.joinable()) {
            if (exited) {
                LOGI("JNI: Joining native OpenConnect thread (session=%lld)...", (long long) current->session_id);
                g_vpn_thread.join();
                LOGI("JNI: Native OpenConnect thread joined successfully");
            } else {
                LOGI("JNI: OpenConnect thread (session=%lld) still blocked after 1500ms; detaching so teardown completes immediately",
                     (long long) current->session_id);
                g_vpn_thread.detach();
            }
        }
        if (g_active_vpn == current) {
            g_active_vpn.reset();
        }
    } else {
        std::lock_guard<std::mutex> lock(g_state_mtx);
        if (g_vpn_thread.joinable()) {
            g_vpn_thread.detach();
        }
    }
}

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

    auto new_state = std::make_shared<vpn_state>();
    new_state->server = native_server ? native_server : "";
    new_state->user = native_user ? native_user : "";
    new_state->pass = native_pass ? native_pass : "";
    new_state->tun_fd = vpn_file_descriptor;
    new_state->session_id = session_id;
    new_state->service_obj = env->NewGlobalRef(thiz);

    if (native_server) env->ReleaseStringUTFChars(server_address, native_server);
    if (native_user) env->ReleaseStringUTFChars(username, native_user);
    if (native_pass) env->ReleaseStringUTFChars(password, native_pass);

    // Ensure any previous session is signaled and cleaned up before starting a new one
    perform_stop_openconnect();

    {
        std::lock_guard<std::mutex> lock(g_state_mtx);
        g_active_vpn = new_state;
        g_vpn_thread = std::thread(vpn_thread_func, new_state);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_interruptOpenConnectNative(JNIEnv *env,
                                                                               jobject thiz) {
    interrupt_active_openconnect();
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_rouzbeh_hybridvpn_service_HybridVpnService_stopOpenConnectNative(JNIEnv *env,
                                                                          jobject thiz) {
    perform_stop_openconnect();
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
