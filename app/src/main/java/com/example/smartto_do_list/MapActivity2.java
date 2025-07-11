package com.example.smartto_do_list;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapActivity2 extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;

    private FloatingActionButton fabCurrentLocation;
    private Button btnSelectLocation;
    private ImageButton btnClearDetails;
    private LinearLayout bottomDetailsPanel;
    private TextView tvLocationName, tvLatLon;
    private TextInputEditText searchInput;

    private Marker currentMarker = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapview);
        mapView.setMultiTouchControls(true);

        fabCurrentLocation = findViewById(R.id.fab_current_location);
        btnSelectLocation = findViewById(R.id.btn_select_location);
        btnClearDetails = findViewById(R.id.cleardetails);
        bottomDetailsPanel = findViewById(R.id.bottom_details_panel);
        tvLocationName = findViewById(R.id.tv_location_name);
        tvLatLon = findViewById(R.id.tv_lat_lon);
        searchInput = findViewById(R.id.searchbar);

        requestPermissionsIfNecessary();

        fabCurrentLocation.setOnClickListener(v -> {
            if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
                GeoPoint myPoint = new GeoPoint(
                        locationOverlay.getMyLocation().getLatitude(),
                        locationOverlay.getMyLocation().getLongitude());

                locationOverlay.enableFollowLocation();

                // Smooth zoom animation from current level to 18.75
                final double startZoom = mapView.getZoomLevelDouble();
                final double targetZoom = 18.75;

                ValueAnimator zoomAnimator = ValueAnimator.ofFloat((float) startZoom, (float) targetZoom);
                zoomAnimator.setDuration(800); // Smooth animation duration
                zoomAnimator.addUpdateListener(animation -> {
                    float animatedZoom = (float) animation.getAnimatedValue();
                    mapView.getController().setZoom(animatedZoom);
                });
                zoomAnimator.start();

                // Smooth animate to current location
                mapView.getController().animateTo(myPoint);
            }
        });

        btnSelectLocation.setOnClickListener(v -> handleLocationSelection());

        btnClearDetails.setOnClickListener(v -> clearDroppedPinAndDetails());

        setupSearchInput();
        setupLongPressToDropPin();

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                if (currentMarker != null && currentMarker.getPosition() != null) {
                    checkSelectButtonVisibility();
                }
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                if (currentMarker != null && currentMarker.getPosition() != null) {
                    checkSelectButtonVisibility();
                }
                return true;
            }
        });
    }

    private void enableUserLocation() {
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();
        mapView.getOverlays().add(locationOverlay);

        // Ensure map orientation is north-up
        mapView.setMapOrientation(0.0f);

        locationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            if (locationOverlay.getMyLocation() != null) {
                GeoPoint myPoint = new GeoPoint(
                        locationOverlay.getMyLocation().getLatitude(),
                        locationOverlay.getMyLocation().getLongitude());

                mapView.getController().setZoom(17.5);
                mapView.getController().animateTo(myPoint);
            }
        }));
    }

    private void requestPermissionsIfNecessary() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        } else {
            enableUserLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) enableUserLocation();
            else Snackbar.make(mapView, "Location permission denied", Snackbar.LENGTH_SHORT).show();
        }
    }


    private void setupLongPressToDropPin() {
        Overlay longPressOverlay = new Overlay() {
            @Override
            public boolean onLongPress(MotionEvent e, MapView mapView) {
                GeoPoint tappedPoint = (GeoPoint) mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                runOnUiThread(() -> dropPinAtLocation(tappedPoint));
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e, MapView mapView) {
                return false;
            }
        };
        mapView.getOverlays().add(longPressOverlay);
    }

    private void dropPinAtLocation(GeoPoint geoPoint) {
        if (geoPoint == null) return;

        // Remove old marker if it exists
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
        }

        currentMarker = new Marker(mapView);
        currentMarker.setPosition(geoPoint);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setInfoWindow(null);

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        String addressText = "Unnamed Location";
        try {
            List<Address> addresses = geocoder.getFromLocation(geoPoint.getLatitude(), geoPoint.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                addressText = addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        mapView.getOverlays().add(currentMarker);
        mapView.invalidate();

        showBottomDetailsPanel(addressText, geoPoint);
        showSelectButtonAtLocation(geoPoint);
        repositionFabAboveBottomPanel();
    }

    private void showBottomDetailsPanel(String locationName, GeoPoint point) {
        bottomDetailsPanel.setVisibility(View.VISIBLE);
        tvLocationName.setText(locationName);
        tvLatLon.setText(String.format(Locale.getDefault(), "%.5f, %.5f", point.getLatitude(), point.getLongitude()));
    }

    private void showSelectButtonAtLocation(GeoPoint point) {
        if (point == null) {
            btnSelectLocation.setVisibility(View.GONE);
            return;
        }
        btnSelectLocation.setVisibility(View.VISIBLE);

        Projection projection = mapView.getProjection();
        Point screenPoint = projection.toPixels(point, null);

        int screenWidth = mapView.getWidth();
        int screenHeight = mapView.getHeight();

        btnSelectLocation.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int buttonWidth = btnSelectLocation.getMeasuredWidth();
        int buttonHeight = btnSelectLocation.getMeasuredHeight();

        int x = screenPoint.x - buttonWidth / 2;
        int y = screenPoint.y - buttonHeight - 130;

        x = Math.max(0, Math.min(x, screenWidth - buttonWidth));
        y = Math.max(0, Math.min(y, screenHeight - buttonHeight));

        btnSelectLocation.setX(x);
        btnSelectLocation.setY(y);
    }

    private void checkSelectButtonVisibility() {
        if (currentMarker == null || currentMarker.getPosition() == null) {
            btnSelectLocation.setVisibility(View.GONE);
            return;
        }
        Projection projection = mapView.getProjection();
        Point screenPoint = projection.toPixels(currentMarker.getPosition(), null);

        int screenWidth = mapView.getWidth();
        int screenHeight = mapView.getHeight();

        if (screenPoint.x < -50 || screenPoint.x > screenWidth + 50
                || screenPoint.y < -50 || screenPoint.y > screenHeight + 50) {
            btnSelectLocation.setVisibility(View.GONE);
        } else {
            btnSelectLocation.setVisibility(View.VISIBLE);
            showSelectButtonAtLocation(currentMarker.getPosition());
        }
    }

    private void handleLocationSelection() {
        if (currentMarker == null || currentMarker.getPosition() == null) {
            Snackbar.make(mapView, "Please select a location first", Snackbar.LENGTH_SHORT).show();
            return;
        }

        GeoPoint point = currentMarker.getPosition();
        String locationName = tvLocationName.getText().toString();

        Intent resultIntent = new Intent();

        resultIntent.putExtra("selected_lat", point.getLatitude());
        resultIntent.putExtra("selected_lon", point.getLongitude());
        resultIntent.putExtra("selected_name", locationName);

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void setupSearchInput() {
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Snackbar.make(mapView, "Enter location to search", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (locationOverlay != null) {
            locationOverlay.disableFollowLocation(); // 👈 important
        }

        // Check for coordinate pattern
        if (query.matches("^\\s*0+(\\.0+)?\\s*,\\s*0+(\\.0+)?\\s*$")) {
            GeoPoint zeroPoint = new GeoPoint(0.0, 0.0);
            mapView.getController().animateTo(zeroPoint);
            dropPinAtLocation(zeroPoint);
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            return;
        }

        // Normal geocoding search
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                GeoPoint point = new GeoPoint(address.getLatitude(), address.getLongitude());
                mapView.getController().animateTo(point);
                dropPinAtLocation(point);

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            } else {
                Snackbar.make(mapView, "Location not found", Snackbar.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Snackbar.make(mapView, "Geocoder error", Snackbar.LENGTH_SHORT).show();
        }
    }


    private void repositionFabAboveBottomPanel() {
        bottomDetailsPanel.post(() -> {
            int bottomPanelHeight = bottomDetailsPanel.getHeight();
            fabCurrentLocation.animate()
                    .translationY(-bottomPanelHeight - 32)
                    .setDuration(300)
                    .start();
        });
    }

    private void resetFabPosition() {
        fabCurrentLocation.animate()
                .translationY(0)
                .setDuration(300)
                .start();
    }

    private void clearDroppedPinAndDetails() {
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
            currentMarker = null;
            mapView.invalidate();
        }
        bottomDetailsPanel.setVisibility(View.GONE);
        btnSelectLocation.setVisibility(View.GONE);
        resetFabPosition();
    }
}
