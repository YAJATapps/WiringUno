/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.unoqcontrol;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ArduinoRouterClient.Listener, PinAdapter.OnPinToggleListener {

    private final List<PinModel> allPins = new ArrayList<>();
    private PinAdapter pinAdapter;
    private ArduinoRouterClient routerClient;

    private View connectionDot;
    private TextView connectionStatusText;
    private MaterialButton btnReconnect;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private TextView logTextView;
    private ScrollView logScrollView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupPinData();
        setupRecyclerView();
        setupActions();

        routerClient = new ArduinoRouterClient(this);
        routerClient.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (routerClient != null) {
            routerClient.stop();
        }
    }

    private void initViews() {
        connectionDot = findViewById(R.id.connection_dot);
        connectionStatusText = findViewById(R.id.connection_status_text);
        btnReconnect = findViewById(R.id.btn_reconnect);
        tabLayout = findViewById(R.id.tab_layout);
        recyclerView = findViewById(R.id.recycler_pins);
        logTextView = findViewById(R.id.log_text);
        logScrollView = findViewById(R.id.log_scroll);

        btnReconnect.setOnClickListener(v -> {
            logMessage("[ACTION] Manual reconnect requested...");
            if (routerClient != null) {
                routerClient.reconnect();
            }
        });

        findViewById(R.id.btn_clear_log).setOnClickListener(v -> logTextView.setText(""));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        pinAdapter.filterByCategory(PinModel.Category.DIGITAL);
                        break;
                    case 1:
                        pinAdapter.filterByCategory(PinModel.Category.ANALOG);
                        break;
                    case 2:
                        pinAdapter.filterByCategory(PinModel.Category.LED);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupPinData() {
        // Digital Header Pins
        allPins.add(new PinModel("D0", "Pin 0", "UART RX / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D1", "Pin 1", "UART TX / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D2", "Pin 2", "EXT INT / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D3", "Pin 3", "PWM ~ / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D4", "Pin 4", "GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D5", "Pin 5", "PWM ~ / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D6", "Pin 6", "PWM ~ / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D7", "Pin 7", "GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D8", "Pin 8", "GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D9", "Pin 9", "PWM ~ / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D10", "Pin 10", "PWM ~ / SPI CS / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D11", "Pin 11", "PWM ~ / SPI COPI / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D12", "Pin 12", "SPI CIPO / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D13", "Pin 13", "Built-in LED / SPI SCK", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D20", "Pin 20", "I2C SDA / GPIO", PinModel.Category.DIGITAL, false));
        allPins.add(new PinModel("D21", "Pin 21", "I2C SCL / GPIO", PinModel.Category.DIGITAL, false));

        // Analog Header Pins
        allPins.add(new PinModel("A0", "Pin A0", "Analog In 0 / GPIO", PinModel.Category.ANALOG, false));
        allPins.add(new PinModel("A1", "Pin A1", "Analog In 1 / GPIO", PinModel.Category.ANALOG, false));
        allPins.add(new PinModel("A2", "Pin A2", "Analog In 2 / GPIO", PinModel.Category.ANALOG, false));
        allPins.add(new PinModel("A3", "Pin A3", "Analog In 3 / GPIO", PinModel.Category.ANALOG, false));
        allPins.add(new PinModel("A4", "Pin A4", "Analog In 4 / I2C SDA", PinModel.Category.ANALOG, false));
        allPins.add(new PinModel("A5", "Pin A5", "Analog In 5 / I2C SCL", PinModel.Category.ANALOG, false));

        // Onboard RGB LEDs (STM32 - Active Low)
        allPins.add(new PinModel("LED3_R", "LED3", "Onboard RGB3 - Red Channel", PinModel.Category.LED, true));
        allPins.add(new PinModel("LED3_G", "LED3", "Onboard RGB3 - Green Channel", PinModel.Category.LED, true));
        allPins.add(new PinModel("LED3_B", "LED3", "Onboard RGB3 - Blue Channel", PinModel.Category.LED, true));
        allPins.add(new PinModel("LED4_R", "LED4", "Onboard RGB4 - Red Channel", PinModel.Category.LED, true));
        allPins.add(new PinModel("LED4_G", "LED4", "Onboard RGB4 - Green Channel", PinModel.Category.LED, true));
        allPins.add(new PinModel("LED4_B", "LED4", "Onboard RGB4 - Blue Channel", PinModel.Category.LED, true));
    }

    private void setupRecyclerView() {
        pinAdapter = new PinAdapter(allPins, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(pinAdapter);
    }

    private void setupActions() {
        findViewById(R.id.btn_all_high).setOnClickListener(v -> setBatchPins(true));
        findViewById(R.id.btn_all_low).setOnClickListener(v -> setBatchPins(false));

        findViewById(R.id.btn_blink_d13).setOnClickListener(v -> {
            logMessage("[ACTION] Starting D13 blink test (5 cycles)...");
            new Thread(() -> {
                PinModel d13 = findPin("D13");
                for (int i = 0; i < 5; i++) {
                    if (d13 != null) d13.setState(true);
                    routerClient.setPin("D13", true);
                    mainHandler.post(() -> pinAdapter.notifyPinChanged("D13"));
                    sleep(300);

                    if (d13 != null) d13.setState(false);
                    routerClient.setPin("D13", false);
                    mainHandler.post(() -> pinAdapter.notifyPinChanged("D13"));
                    sleep(300);
                }
                logMessage("[ACTION] D13 blink test complete.");
            }).start();
        });

        findViewById(R.id.btn_cycle_leds).setOnClickListener(v -> {
            logMessage("[ACTION] Cycling Onboard RGB LEDs...");
            new Thread(() -> {
                String[] leds = {"LED3_R", "LED3_G", "LED3_B", "LED4_R", "LED4_G", "LED4_B"};
                // Turn all off first
                for (String led : leds) {
                    PinModel p = findPin(led);
                    if (p != null) {
                        p.setState(false);
                        routerClient.setPin(led, p.getHardwareState());
                        mainHandler.post(() -> pinAdapter.notifyPinChanged(led));
                    }
                }
                sleep(200);

                // Cycle each one
                for (String led : leds) {
                    PinModel p = findPin(led);
                    if (p != null) {
                        p.setState(true);
                        routerClient.setPin(led, p.getHardwareState());
                        mainHandler.post(() -> pinAdapter.notifyPinChanged(led));
                    }
                    sleep(350);

                    if (p != null) {
                        p.setState(false);
                        routerClient.setPin(led, p.getHardwareState());
                        mainHandler.post(() -> pinAdapter.notifyPinChanged(led));
                    }
                    sleep(150);
                }
                logMessage("[ACTION] LED cycle complete.");
            }).start();
        });
    }

    private void setBatchPins(boolean state) {
        int selectedTab = tabLayout.getSelectedTabPosition();
        PinModel.Category cat = (selectedTab == 0) ? PinModel.Category.DIGITAL
                : (selectedTab == 1) ? PinModel.Category.ANALOG : PinModel.Category.LED;

        for (PinModel pin : allPins) {
            if (pin.getCategory() == cat) {
                pin.setState(state);
                routerClient.setPin(pin.getName(), pin.getHardwareState());
            }
        }
        mainHandler.post(() -> pinAdapter.notifyAllPinsChanged());
    }

    private PinModel findPin(String name) {
        for (PinModel p : allPins) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void onPinToggled(PinModel pin, boolean isChecked) {
        routerClient.setPin(pin.getName(), pin.getHardwareState());
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (connectionDot != null) {
                connectionDot.setBackgroundResource(connected ? R.drawable.indicator_dot_green : R.drawable.indicator_dot_red);
            }
            if (connectionStatusText != null) {
                connectionStatusText.setText(message);
            }
        });
    }

    @Override
    public void onLogMessage(String log) {
        logMessage(log);
    }

    @Override
    public void onPinResponse(int msgId, String pinName, boolean success) {
        // Response processed
    }

    private void logMessage(String text) {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed() || logTextView == null) return;
            String timestamp = timeFormat.format(new Date());
            String line = "[" + timestamp + "] " + text + "\n";
            logTextView.append(line);
            if (logScrollView != null) {
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}
