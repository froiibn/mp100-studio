#define COBJMACROS
#include <windows.h>
#include <initguid.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#include <ks.h>
#include <ksmedia.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct {
    IAudioClient *client;
    IAudioRenderClient *render;
    UINT32 capacity;
    UINT32 block;
    uint64_t submitted;
    BOOL unsigned8;
} Output;

static HRESULT default_client(IAudioClient **client) {
    IMMDeviceEnumerator *enumerator = NULL;
    IMMDevice *device = NULL;
    HRESULT hr = CoCreateInstance(&CLSID_MMDeviceEnumerator, NULL, CLSCTX_INPROC_SERVER,
                                  &IID_IMMDeviceEnumerator, (void **) &enumerator);
    if (SUCCEEDED(hr)) {
        hr = IMMDeviceEnumerator_GetDefaultAudioEndpoint(enumerator, eRender, eMultimedia, &device);
        if (SUCCEEDED(hr)) {
            hr = IMMDevice_Activate(device, &IID_IAudioClient, CLSCTX_INPROC_SERVER, NULL, (void **) client);
            IMMDevice_Release(device);
        }
        IMMDeviceEnumerator_Release(enumerator);
    }
    return hr;
}

static void release_output(Output *output) {
    if (output->render != NULL) {
        IAudioRenderClient_Release(output->render);
    }
    if (output->client != NULL) {
        IAudioClient_Release(output->client);
    }
    free(output);
}

static void throw_output_error(JNIEnv *env, const char *operation, HRESULT result) {
    jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (error != NULL) {
        char message[96];
        snprintf(message, sizeof(message), "WASAPI %s failed (0x%08lx)", operation, (unsigned long) result);
        (*env)->ThrowNew(env, error, message);
    }
}

JNIEXPORT jint JNICALL Java_mp100_v2_audio_WasapiOutput_defaultSampleRate(JNIEnv *env, jclass type) {
    (void) env;
    (void) type;
    if (FAILED(CoInitializeEx(NULL, COINIT_MULTITHREADED))) {
        return 0;
    }
    IAudioClient *client = NULL;
    WAVEFORMATEX *mix = NULL;
    jint rate = 0;
    if (SUCCEEDED(default_client(&client))) {
        if (SUCCEEDED(IAudioClient_GetMixFormat(client, &mix))) {
            rate = (jint) mix->nSamplesPerSec;
            CoTaskMemFree(mix);
        }
        IAudioClient_Release(client);
    }
    CoUninitialize();
    return rate;
}

JNIEXPORT jlong JNICALL Java_mp100_v2_audio_WasapiOutput_open0(
        JNIEnv *env, jclass type, jint rate, jint channels, jint bits, jboolean floating) {
    (void) env;
    (void) type;
    if (FAILED(CoInitializeEx(NULL, COINIT_MULTITHREADED))) {
        return 0;
    }
    Output *output = calloc(1, sizeof(*output));
    if (output == NULL) {
        CoUninitialize();
        return 0;
    }
    output->block = (UINT32) (channels * bits / 8);
    output->unsigned8 = bits == 8 && !floating;
    WAVEFORMATEXTENSIBLE wave = {0};
    wave.Format.wFormatTag = WAVE_FORMAT_EXTENSIBLE;
    wave.Format.nChannels = (WORD) channels;
    wave.Format.nSamplesPerSec = (DWORD) rate;
    wave.Format.wBitsPerSample = (WORD) bits;
    wave.Format.nBlockAlign = (WORD) output->block;
    wave.Format.nAvgBytesPerSec = wave.Format.nSamplesPerSec * output->block;
    wave.Format.cbSize = sizeof(wave) - sizeof(wave.Format);
    wave.Samples.wValidBitsPerSample = (WORD) bits;
    wave.dwChannelMask = channels == 1 ? SPEAKER_FRONT_CENTER : SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT;
    wave.SubFormat = floating ? KSDATAFORMAT_SUBTYPE_IEEE_FLOAT : KSDATAFORMAT_SUBTYPE_PCM;

    HRESULT hr = default_client(&output->client);
    if (SUCCEEDED(hr)) {
        WAVEFORMATEX *closest = NULL;
        hr = IAudioClient_IsFormatSupported(output->client, AUDCLNT_SHAREMODE_SHARED, &wave.Format, &closest);
        CoTaskMemFree(closest);
        if (hr == S_OK) {
            hr = IAudioClient_Initialize(output->client, AUDCLNT_SHAREMODE_SHARED, 0,
                                         600000, 0, &wave.Format, NULL);
            if (SUCCEEDED(hr)) {
                hr = IAudioClient_GetBufferSize(output->client, &output->capacity);
            }
            if (SUCCEEDED(hr)) {
                hr = IAudioClient_GetService(output->client, &IID_IAudioRenderClient, (void **) &output->render);
            }
            if (SUCCEEDED(hr)) {
                return (jlong) (intptr_t) output;
            }
        }
    }
    release_output(output);
    CoUninitialize();
    return 0;
}

JNIEXPORT void JNICALL Java_mp100_v2_audio_WasapiOutput_setRunning0(
        JNIEnv *env, jclass type, jlong handle, jboolean running) {
    (void) type;
    Output *output = (Output *) (intptr_t) handle;
    HRESULT hr = running ? IAudioClient_Start(output->client) : IAudioClient_Stop(output->client);
    if (FAILED(hr)) {
        throw_output_error(env, running ? "start" : "pause", hr);
    }
}

JNIEXPORT jint JNICALL Java_mp100_v2_audio_WasapiOutput_available0(
        JNIEnv *env, jclass type, jlong handle) {
    (void) type;
    Output *output = (Output *) (intptr_t) handle;
    UINT32 padding;
    HRESULT hr = IAudioClient_GetCurrentPadding(output->client, &padding);
    if (FAILED(hr)) {
        throw_output_error(env, "buffer query", hr);
        return 0;
    }
    return (jint) ((output->capacity - padding) * output->block);
}

JNIEXPORT jint JNICALL Java_mp100_v2_audio_WasapiOutput_write0(
        JNIEnv *env, jclass type, jlong handle, jbyteArray samples, jint length) {
    (void) type;
    Output *output = (Output *) (intptr_t) handle;
    UINT32 frames = (UINT32) length / output->block;
    BYTE *buffer = NULL;
    HRESULT hr = IAudioRenderClient_GetBuffer(output->render, frames, &buffer);
    if (FAILED(hr)) {
        throw_output_error(env, "get buffer", hr);
        return 0;
    }
    (*env)->GetByteArrayRegion(env, samples, 0, length, (jbyte *) buffer);
    if ((*env)->ExceptionCheck(env)) {
        IAudioRenderClient_ReleaseBuffer(output->render, frames, AUDCLNT_BUFFERFLAGS_SILENT);
        return 0;
    }
    if (output->unsigned8) {
        for (jint i = 0; i < length; i++) {
            buffer[i] ^= 0x80;
        }
    }
    hr = IAudioRenderClient_ReleaseBuffer(output->render, frames, 0);
    if (FAILED(hr)) {
        throw_output_error(env, "submit buffer", hr);
        return 0;
    }
    output->submitted += frames;
    return length;
}

JNIEXPORT jlong JNICALL Java_mp100_v2_audio_WasapiOutput_framesPlayed0(
        JNIEnv *env, jclass type, jlong handle) {
    (void) type;
    Output *output = (Output *) (intptr_t) handle;
    UINT32 padding;
    HRESULT hr = IAudioClient_GetCurrentPadding(output->client, &padding);
    if (FAILED(hr)) {
        throw_output_error(env, "playback position", hr);
        return 0;
    }
    return padding < output->submitted ? (jlong) (output->submitted - padding) : 0;
}

JNIEXPORT void JNICALL Java_mp100_v2_audio_WasapiOutput_close0(JNIEnv *env, jclass type, jlong handle) {
    (void) env;
    (void) type;
    Output *output = (Output *) (intptr_t) handle;
    IAudioClient_Stop(output->client);
    IAudioClient_Reset(output->client);
    release_output(output);
    CoUninitialize();
}
