/*
 * Copyright (C) 2026 Yajat Kumar <yajatapps3@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yajatkumar.wiringuno;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Communicates with the official arduino-router daemon via Unix Domain Socket
 * at /dev/socket/arduino-router.sock using MessagePack-RPC.
 */
public class ArduinoRouterClient {

    private static final String TAG = "ArduinoRouterClient";
    public static final String SOCKET_PATH = "/dev/socket/arduino-router.sock";

    public interface Listener {
        void onConnectionStateChanged(boolean connected, String message);
        void onLogMessage(String log);
        void onPinResponse(int msgId, String pinName, boolean success);
    }

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private Thread connectionThread;

    private LocalSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger msgIdCounter = new AtomicInteger(1);

    public ArduinoRouterClient(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            connectionThread = new Thread(this::connectionLoop, "ArduinoRouter-Conn");
            connectionThread.setDaemon(true);
            connectionThread.start();
        }
    }

    public void stop() {
        isRunning.set(false);
        closeSocket();
        if (connectionThread != null) {
            connectionThread.interrupt();
            connectionThread = null;
        }
        sendExecutor.shutdownNow();
    }

    public void reconnect() {
        sendExecutor.submit(this::closeSocket);
    }

    public void setPin(String pinName, boolean hardwareState) {
        int msgId = msgIdCounter.getAndIncrement();
        sendExecutor.submit(() -> {
            if (!isConnected.get() || outputStream == null) {
                postLog("[ERR] Cannot send: not connected to " + SOCKET_PATH);
                return;
            }

            try {
                byte[] packet = MessagePackRpc.encodeSetPinByName(msgId, pinName, hardwareState);
                outputStream.write(packet);
                outputStream.flush();

                postLog(String.format("TX [%d] set_pin_by_name('%s', %s) [%d B]",
                        msgId, pinName, hardwareState, packet.length));

            } catch (Exception e) {
                Log.e(TAG, "Failed to send RPC request", e);
                postLog("[ERR] TX failed: " + e.getMessage());
                closeSocket();
            }
        });
    }

    private void connectionLoop() {
        while (isRunning.get()) {
            if (!isConnected.get()) {
                connectSocket();
            }

            if (isConnected.get() && inputStream != null) {
                readLoop();
            }

            if (isRunning.get()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void connectSocket() {
        postConnectionState(false, "Connecting to " + SOCKET_PATH + "...");
        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM);
            socket.connect(address);

            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            isConnected.set(true);
            postConnectionState(true, "Connected to " + SOCKET_PATH);
            postLog("[OK] Connected to arduino-router at " + SOCKET_PATH);

        } catch (Exception e) {
            isConnected.set(false);
            postConnectionState(false, "Disconnected: " + e.getMessage());
            closeSocket();
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[1024];
        try {
            while (isRunning.get() && isConnected.get()) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead <= 0) {
                    break; // EOF or error
                }

                MessagePackRpc.Response resp = MessagePackRpc.parseResponse(buffer, 0, bytesRead);
                if (resp != null) {
                    postLog(String.format("RX [%d] %s",
                            resp.msgId, resp.isSuccess ? "OK" : ("ERR: " + resp.error)));
                } else {
                    postLog(String.format("RX (%d B) raw", bytesRead));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Read loop error", e);
        } finally {
            closeSocket();
            postConnectionState(false, "Disconnected from arduino-router");
            postLog("[INFO] Disconnected from arduino-router");
        }
    }

    private synchronized void closeSocket() {
        isConnected.set(false);
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket = null;

        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        outputStream = null;

        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception ignored) {}
        inputStream = null;
    }

    private void postConnectionState(boolean connected, String message) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onConnectionStateChanged(connected, message);
            }
        });
    }

    private void postLog(String text) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onLogMessage(text);
            }
        });
    }
}
