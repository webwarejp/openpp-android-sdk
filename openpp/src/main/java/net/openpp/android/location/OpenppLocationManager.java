/*
 * Copyright (C) 2015 webware,Inc
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
package net.openpp.android.location;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.openpp.android.push.OpenppPushManager;

import java.io.IOException;
import java.util.HashMap;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppLocationManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener  {

    private static final String PARAM_LOCATION_LATITUDE = "location_latitude";
    private static final String PARAM_LOCATION_LONGITUDE = "location_longitude";
    private static final long UPDATE_INTERVAL = 60000;
    private static final long FASTEST_UPDATE_INTERVAL = 30000;
    // Tag used on log messages.
    private static final String TAG = "OpenppLocationManager";

    private static final OpenppLocationManager mInstance = new OpenppLocationManager();
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    /**
     * Constructor.
     */
    private OpenppLocationManager() {
    }

    /**
     * Gets the OpenppLocationManager instance.
     * @return OpenppLocationManager instance
     */
    public static OpenppLocationManager getInstance() {
        return mInstance;
    }

    /**
     * Starts the service to get the device's location and register it to the backend server.
     * @param context application context
     */
    public void startLocationService(Context context) {
        if (null == mContext) {
            mContext = context;
            buildGoogleApiClient();
        }
    }

    /**
     * Builds Google Api client.
     */
    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (lastLocation != null) {
            registerLocation(lastLocation);
        }

        startLocationUpdates();
    }

    /**
     * Requests location updates to the Location Service.
     */
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "Disconnected from the service temporarily: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "An error occurred when connecting the client to the service.");
        // Register this device without the location information.
        OpenppPushManager.getInstance().registerInBackground();
    }

    @Override
    public void onLocationChanged(Location location) {
        registerLocation(location);
    }

    /**
     * Registers the device location to the backend server.
     * @param location current device's location
     */
    private void registerLocation(Location location) {
        new AsyncTask<Location, Void, Void>() {
            @Override
            protected Void doInBackground(Location... params) {
                Location location = params[0];

                HashMap<String, String> bodyParam = new HashMap<>();
                bodyParam.put(PARAM_LOCATION_LATITUDE, String.valueOf(location.getLatitude()));
                bodyParam.put(PARAM_LOCATION_LONGITUDE, String.valueOf(location.getLatitude()));
                try {
                    OpenppPushManager.getInstance().sendRegistrationRequest(bodyParam);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to resister the location: " + e.getMessage());
                }
                return null;
            }
        }.execute(location, null, null);
    }
}
