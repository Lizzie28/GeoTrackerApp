package com.example.geotrackerapp;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private GeofencingClient geofencingClient;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final float GEOFENCE_RADIUS = 200; // 200 meters as requested

    public static final List<LatLng> savedLocations = new ArrayList<>();
    public static final List<String> savedLocationNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        checkAndRequestPermissions();

        EditText nameInput = findViewById(R.id.locationNameInput);
        EditText latInput = findViewById(R.id.locationLatInput);
        EditText lngInput = findViewById(R.id.locationLngInput);
        Button addBtn = findViewById(R.id.addLocationBtn);
        Button viewSavedBtn = findViewById(R.id.btnViewSaved);

        viewSavedBtn.setOnClickListener(v ->
                startActivity(new Intent(MapsActivity.this, SavedLocationsActivity.class))
        );

        loadSavedLocationsFromDatabase();

        addBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString();
            String latStr = latInput.getText().toString();
            String lngStr = lngInput.getText().toString();

            if (name.isEmpty() || latStr.isEmpty() || lngStr.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double lat, lng;
            try {
                lat = Double.parseDouble(latStr);
                lng = Double.parseDouble(lngStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show();
                return;
            }

            saveNewLocation(name, lat, lng);
            nameInput.setText("");
            latInput.setText("");
            lngInput.setText("");
        });
    }

    private void saveNewLocation(String name, double lat, double lng) {
        LatLng newLocation = new LatLng(lat, lng);
        savedLocations.add(newLocation);
        savedLocationNames.add(name);

        saveLocationToDatabase(name, lat, lng);
        
        if (mMap != null) {
            addMarkerAndCircle(newLocation, name);
        }

        addGeofence(newLocation, name);
        Toast.makeText(this, "Location added: " + name, Toast.LENGTH_SHORT).show();
    }

    private void addMarkerAndCircle(LatLng latLng, String title) {
        mMap.addMarker(new MarkerOptions().position(latLng).title(title));
        mMap.addCircle(new CircleOptions()
                .center(latLng)
                .radius(GEOFENCE_RADIUS)
                .strokeColor(0x5500FF00)
                .fillColor(0x2200FF00));
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), LOCATION_PERMISSION_REQUEST_CODE);
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
                this, 0, new Intent(this, GeofenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, pendingIntent)
                    .addOnFailureListener(e -> Toast.makeText(this, "Geofence failed", Toast.LENGTH_SHORT).show());
        }
    }

    private void loadSavedLocationsFromDatabase() {
        new Thread(() -> {
            List<LocationEntity> allLocations = AppDatabase.getInstance(this).locationDao().getAllLocations();
            savedLocations.clear();
            savedLocationNames.clear();

            for (LocationEntity loc : allLocations) {
                LatLng latLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                savedLocations.add(latLng);
                savedLocationNames.add(loc.getName());

                runOnUiThread(() -> {
                    if (mMap != null) {
                        addMarkerAndCircle(latLng, loc.getName());
                    }
                    addGeofence(latLng, loc.getName());
                });
            }
        }).start();
    }

    private void saveLocationToDatabase(String name, double lat, double lng) {
        new Thread(() -> {
            LocationEntity location = new LocationEntity(name, lat, lng);
            AppDatabase.getInstance(this).locationDao().insert(location);
        }).start();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 15));
                }
            });
        }

        mMap.setOnMapLongClickListener(latLng -> saveNewLocation("Pinned Location", latLng.latitude, latLng.longitude));

        // Handle navigation from SavedLocationsActivity
        Intent intent = getIntent();
        if (intent.hasExtra("navigateLat") && intent.hasExtra("navigateLng")) {
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
            // Fallback to browser or just show on internal map
            LatLng nav = new LatLng(lat, lng);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(nav, 18));
            Toast.makeText(this, "Opening directions...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mMap != null && requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            onMapReady(mMap);
        }
    }
}