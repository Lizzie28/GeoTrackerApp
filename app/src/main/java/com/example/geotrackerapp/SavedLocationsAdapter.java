package com.example.geotrackerapp;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class SavedLocationsAdapter extends RecyclerView.Adapter<SavedLocationsAdapter.ViewHolder> {

    private final Context context;
    private final List<LocationEntity> locations;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(LatLng latLng);
    }

    public SavedLocationsAdapter(Context context, List<LocationEntity> locations, OnItemClickListener listener) {
        this.context = context;
        this.locations = locations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationEntity location = locations.get(position);
        String name = location.getName();
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        holder.textView.setText(name != null ? name : "Unnamed Location");

        // Tap to navigate
        holder.itemView.setOnClickListener(v -> {
            listener.onItemClick(latLng);
        });

        // Long press for options (Edit/Delete)
        holder.itemView.setOnLongClickListener(v -> {
            String[] options = {"Edit/Rename", "Delete"};
            new AlertDialog.Builder(context)
                    .setTitle("Options for " + name)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showEditDialog(location, position);
                        } else {
                            showDeleteDialog(location, position);
                        }
                    })
                    .show();
            return true;
        });
    }

    private void showEditDialog(LocationEntity location, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Location");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText nameInput = new EditText(context);
        nameInput.setHint("Name");
        nameInput.setText(location.getName());
        layout.addView(nameInput);

        final EditText latInput = new EditText(context);
        latInput.setHint("Latitude");
        latInput.setText(String.valueOf(location.getLatitude()));
        layout.addView(latInput);

        final EditText lngInput = new EditText(context);
        lngInput.setHint("Longitude");
        lngInput.setText(String.valueOf(location.getLongitude()));
        layout.addView(lngInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString();
            String latStr = latInput.getText().toString();
            String lngStr = lngInput.getText().toString();

            if (!newName.isEmpty() && !latStr.isEmpty() && !lngStr.isEmpty()) {
                try {
                    double newLat = Double.parseDouble(latStr);
                    double newLng = Double.parseDouble(lngStr);

                    location.setName(newName);
                    location.setLatitude(newLat);
                    location.setLongitude(newLng);

                    new Thread(() -> {
                        AppDatabase.getInstance(context).locationDao().update(location);
                        ((SavedLocationsActivity) context).runOnUiThread(() -> {
                            notifyItemChanged(position);
                            Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show();
                        });
                    }).start();

                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(LocationEntity location, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Location")
                .setMessage("Do you want to delete " + location.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    new Thread(() -> {
                        AppDatabase.getInstance(context).locationDao().delete(location);
                        ((SavedLocationsActivity) context).runOnUiThread(() -> {
                            locations.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, locations.size());
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}