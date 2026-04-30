package com.example.geotrackerapp;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private GeofencingClient geofencingClient;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final float GEOFENCE_RADIUS = 200;

    private EditText nameInput, latInput, lngInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        nameInput = findViewById(R.id.locationNameInput);
        latInput = findViewById(R.id.locationLatInput);
        lngInput = findViewById(R.id.locationLngInput);
        Button addBtn = findViewById(R.id.addLocationBtn);
        Button viewSavedBtn = findViewById(R.id.btnViewSaved);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        checkAndRequestPermissions();
        setCurrentLocationAsDefault();

        // UX: Auto-generate coordinates from name (Geocoding)
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 3) { // Trigger search after 3 characters
                    lookupAddress(s.toString());
                }
            }
        });

        viewSavedBtn.setOnClickListener(v ->
                startActivity(new Intent(MapsActivity.this, SavedLocationsActivity.class))
        );

        addBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString();
            String latStr = latInput.getText().toString();
            String lngStr = lngInput.getText().toString();

            if (name.isEmpty() || latStr.isEmpty() || lngStr.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double lat = Double.parseDouble(latStr);
                double lng = Double.parseDouble(lngStr);
                saveNewLocation(name, lat, lng);
                nameInput.setText("");
                setCurrentLocationAsDefault(); // Reset to current location after add
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show();
            }
        });

        loadSavedLocationsFromDatabase();
    }

    private void setCurrentLocationAsDefault() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    latInput.setText(String.valueOf(location.getLatitude()));
                    lngInput.setText(String.valueOf(location.getLongitude()));
                }
            });
        }
    }

    private void lookupAddress(String locationName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocationName(locationName, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    runOnUiThread(() -> {
                        // Only update if the user isn't currently typing in the lat/lng fields
                        if (!latInput.hasFocus() && !lngInput.hasFocus()) {
                            latInput.setText(String.valueOf(address.getLatitude()));
                            lngInput.setText(String.valueOf(address.getLongitude()));
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void saveNewLocation(String name, double lat, double lng) {
        new Thread(() -> {
            LocationEntity location = new LocationEntity(name, lat, lng);
            AppDatabase.getInstance(this).locationDao().insert(location);
            runOnUiThread(() -> {
                LatLng latLng = new LatLng(lat, lng);
                addMarkerAndCircle(latLng, name);
                addGeofence(latLng, name);
                Toast.makeText(this, "Location saved: " + name, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void addMarkerAndCircle(LatLng latLng, String title) {
        if (mMap != null) {
            mMap.addMarker(new MarkerOptions().position(latLng).title(title));
            mMap.addCircle(new CircleOptions()
                    .center(latLng)
                    .radius(GEOFENCE_RADIUS)
                    .strokeColor(0x5500FF00)
                    .fillColor(0x2200FF00));
        }
    }

    private void addGeofence(LatLng latLng, String id) {
        Geofence geofence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(latLng.latitude, latLng.longitude, GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, id.hashCode(), new Intent(this, GeofenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, pendingIntent);
        }
    }

    private void loadSavedLocationsFromDatabase() {
        new Thread(() -> {
            List<LocationEntity> all = AppDatabase.getInstance(this).locationDao().getAllLocations();
            runOnUiThread(() -> {
                for (LocationEntity loc : all) {
                    LatLng latLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                    addMarkerAndCircle(latLng, loc.getName());
                    addGeofence(latLng, loc.getName());
                }
            });
        }).start();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMapLongClickListener(latLng -> {
            latInput.setText(String.valueOf(latLng.latitude));
            lngInput.setText(String.valueOf(latLng.longitude));
            nameInput.requestFocus();
            Toast.makeText(this, "Coordinates updated from map", Toast.LENGTH_SHORT).show();
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("navigateLat")) {
            double lat = intent.getDoubleExtra("navigateLat", 0);
            double lng = intent.getDoubleExtra("navigateLng", 0);
            openGoogleMapsDirections(lat, lng);
        }
    }

    private void openGoogleMapsDirections(double lat, double lng) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lng);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 18));
        }
    }
}