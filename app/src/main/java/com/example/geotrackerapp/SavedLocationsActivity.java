package com.example.geotrackerapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class SavedLocationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SavedLocationsAdapter adapter;
    private List<LocationEntity> locationList = new ArrayList<>();
    private AppDatabase db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_locations);

        recyclerView = findViewById(R.id.savedLocationsRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = AppDatabase.getInstance(this);

        adapter = new SavedLocationsAdapter(
                this,
                locationList,
                this::navigateToLocation
        );

        recyclerView.setAdapter(adapter);

        loadLocations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLocations();
    }

    private void loadLocations() {
        new Thread(() -> {
            List<LocationEntity> dbLocations = db.locationDao().getAllLocations();

            runOnUiThread(() -> {
                locationList.clear();
                locationList.addAll(dbLocations);
                adapter.notifyDataSetChanged();
                
                // Keep MapsActivity static lists in sync if needed
                MapsActivity.savedLocationNames.clear();
                MapsActivity.savedLocations.clear();
                for (LocationEntity loc : dbLocations) {
                    MapsActivity.savedLocationNames.add(loc.getName());
                    MapsActivity.savedLocations.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
                }
            });
        }).start();
    }

    private void navigateToLocation(LatLng latLng) {
        Intent intent = new Intent(this, MapsActivity.class);
        intent.putExtra("navigateLat", latLng.latitude);
        intent.putExtra("navigateLng", latLng.longitude);
        startActivity(intent);
    }
}