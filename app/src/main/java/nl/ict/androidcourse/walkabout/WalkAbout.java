package nl.ict.androidcourse.walkabout;

import java.util.ArrayList;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * Activity that contains an interactive Google Map fragment. Users can record
 * a traveled path, mark the map with information and take pictures that become
 * associated with the map.
 */
public class WalkAbout extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

	/** The interactive Google Map fragment. */
	private GoogleMap m_vwMap;
	
	/** The list of locations, each having a latitude and longitude. */
	private ArrayList<LatLng> m_arrPathPoints;

	/** The list of markers, each having a latitude, longitude and title. */
	private ArrayList<Marker> m_arrPicturePoints;

	/** The continuous set of lines drawn between points on the map. */
	private Polyline m_pathLine;

	/** The Location Manager for the map. Used to obtain location status, etc. */
	private LocationManager m_locManager;

	/** Whether or not recording is currently in progress. */
	private boolean m_bRecording = false;

	/** The radius of a Circle drawn on the map, in meters. */
	private static final int CIRCLE_RADIUS = 30;

    /** Constants for the LocationManager. */
	private static final int MIN_TIME_CHANGE = 3000;
	private static final int MIN_DISTANCE_CHANGE = 3;

    /** Google location API client reference */
    private GoogleApiClient mGoogleApiClient;

    /** Request codes for starting new Activities. */
	private static final int ENABLE_GPS_REQUEST_CODE = 1;

	private static final int PICTURE_REQUEST_CODE = 2;
    public static final String TAG = "WalkAbout";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_layout);
        buildGoogleApiClient();
        initLocationData();
        initLayout();
    }
    
    /**
     * Initializes all Location-related data.
     */
    private void initLocationData() {
    	m_locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        m_arrPathPoints = new ArrayList<LatLng>();
        m_arrPicturePoints = new ArrayList<Marker>();
    }
    
    /**
     * Initializes all other data for the application.
     */
	private void initLayout() {
		m_vwMap = ((MapFragment)getFragmentManager().findFragmentById(R.id.map)).getMap();

        if (m_vwMap != null) {
            m_vwMap.setMyLocationEnabled(true);
        } else {
            Log.d(TAG, "Map was null!");
        }

	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (m_locManager != null) {
            if (! m_locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                menu.findItem(R.id.menu_recording).setEnabled(false);
                menu.findItem(R.id.menu_enableGPS).setVisible(true);
            } else {
                menu.findItem(R.id.menu_recording).setEnabled(true);
                menu.findItem(R.id.menu_enableGPS).setVisible(false);
            }
        }

        if (m_bRecording) {
            menu.findItem(R.id.menu_recording).setTitle(R.string.menuTitle_stopRecording);
        } else {
            menu.findItem(R.id.menu_recording).setTitle(R.string.menuTitle_startRecording);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        boolean handled = false;
        switch (item.getItemId()) {
            case R.id.menu_enableGPS:
                Intent in = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(in, ENABLE_GPS_REQUEST_CODE);
                handled=true;
                break;
            case R.id.menu_load:
                Toast.makeText(this, "Menu lOAD pressed", Toast.LENGTH_SHORT).show();
                handled=true;
                break;
            case R.id.menu_save:
                Toast.makeText(this, "Menu Save pressed", Toast.LENGTH_SHORT).show();
                handled=true;
                break;
            case R.id.menu_recording:
                Log.d(TAG, "m_bRecording is " + m_bRecording);
                if (m_bRecording == false) {
                    Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
                    m_bRecording = true;
                } else {
                    Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                    m_bRecording = false;
                }
                invalidateOptionsMenu();
                setRecordingState(m_bRecording);
                handled=true;
                break;
            case R.id.menu_takePicture:
                Toast.makeText(this, "Menu Take Picture pressed", Toast.LENGTH_SHORT).show();
                handled=true;
                break;
            default:
                break;
        }
        return handled ? handled : super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ENABLE_GPS_REQUEST_CODE) {
            // ensure that the menu is set properly after this
            invalidateOptionsMenu();
        }

    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // simplified error handling here
        mGoogleApiClient.connect();
    }

    /**
	 * Switch the application so it is or isn't recording the user's path on the map.
	 *  
	 * @param bRecording
	 * 						Whether or not to start recording.
	 */
	private void setRecordingState(boolean bRecording) {
		if (bRecording) {
            m_arrPathPoints.clear();
            m_vwMap.clear();
            m_pathLine = m_vwMap.addPolyline(new PolylineOptions());
            m_pathLine.setColor(Color.GREEN);

            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            onLocationChanged(mLastLocation);
        }
	}
	
	/**
	 * Writes important map data to a private application file.
	 */
	private void saveRecording() {
		// TODO
	}
	
	/**
	 * Retrieves specific map data that was previously written to a private application file
	 * and initializes both the lists and the map with the new data.
	 */
	private void loadRecording() {
		// TODO
	}

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            if (m_vwMap != null) {
                m_vwMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()),
                        17));
            }
        }
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Simplified connection management here
        Toast.makeText(this, "Unable to connect to Play Services framework!", Toast.LENGTH_LONG).show();
    }

    protected void startLocationUpdates() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "New location received");
        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        if (m_bRecording && (m_arrPathPoints != null)) {
            m_arrPathPoints.add(ll);
            m_pathLine.setPoints(m_arrPathPoints);
        }

        m_vwMap.animateCamera(CameraUpdateFactory.newLatLng(ll));
    }
}