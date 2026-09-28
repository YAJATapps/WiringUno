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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class PinAdapter extends RecyclerView.Adapter<PinAdapter.PinViewHolder> {

    public interface OnPinToggleListener {
        void onPinToggled(PinModel pin, boolean isChecked);
    }

    private final List<PinModel> allPins;
    private final List<PinModel> displayedPins = new ArrayList<>();
    private final OnPinToggleListener toggleListener;
    private PinModel.Category currentCategory = PinModel.Category.DIGITAL;

    public PinAdapter(List<PinModel> allPins, OnPinToggleListener toggleListener) {
        this.allPins = allPins;
        this.toggleListener = toggleListener;
        filterByCategory(PinModel.Category.DIGITAL);
    }

    public void filterByCategory(PinModel.Category category) {
        this.currentCategory = category;
        displayedPins.clear();
        for (PinModel pin : allPins) {
            if (pin.getCategory() == category) {
                displayedPins.add(pin);
            }
        }
        notifyDataSetChanged();
    }

    public void notifyAllPinsChanged() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pin_card, parent, false);
        return new PinViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PinViewHolder holder, int position) {
        PinModel pin = displayedPins.get(position);
        holder.nameView.setText(pin.getName());
        holder.descView.setText(pin.getHeaderPin() + " • " + pin.getDescription());

        // Temporarily clear listener to prevent firing during bind
        holder.switchView.setOnCheckedChangeListener(null);
        holder.switchView.setChecked(pin.getState());

        updateIndicator(holder.indicatorView, pin.getState());

        holder.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pin.setState(isChecked);
            updateIndicator(holder.indicatorView, isChecked);
            if (toggleListener != null) {
                toggleListener.onPinToggled(pin, isChecked);
            }
        });
    }

    private void updateIndicator(View indicator, boolean isOn) {
        indicator.setBackgroundResource(isOn ? R.drawable.indicator_dot_green : R.drawable.indicator_dot_red);
    }

    @Override
    public int getItemCount() {
        return displayedPins.size();
    }

    static class PinViewHolder extends RecyclerView.ViewHolder {
        final View indicatorView;
        final TextView nameView;
        final TextView descView;
        final MaterialSwitch switchView;

        PinViewHolder(@NonNull View itemView) {
            super(itemView);
            indicatorView = itemView.findViewById(R.id.pin_indicator);
            nameView = itemView.findViewById(R.id.pin_name);
            descView = itemView.findViewById(R.id.pin_desc);
            switchView = itemView.findViewById(R.id.pin_switch);
        }
    }
}
