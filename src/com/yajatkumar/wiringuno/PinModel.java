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

public class PinModel {
    public enum Category {
        DIGITAL,
        ANALOG,
        LED
    }

    private final String name;
    private final String headerPin;
    private final String description;
    private final Category category;
    private final boolean activeLow;
    private boolean state; // logical state: true = ON/HIGH, false = OFF/LOW

    public PinModel(String name, String headerPin, String description, Category category, boolean activeLow) {
        this.name = name;
        this.headerPin = headerPin;
        this.description = description;
        this.category = category;
        this.activeLow = activeLow;
        this.state = false;
    }

    public String getName() {
        return name;
    }

    public String getHeaderPin() {
        return headerPin;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isActiveLow() {
        return activeLow;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    /**
     * Compute the hardware boolean to send over MessagePack-RPC,
     * accounting for active-low inversion on STM32 RGB LEDs.
     */
    public boolean getHardwareState() {
        return activeLow ? !state : state;
    }
}
