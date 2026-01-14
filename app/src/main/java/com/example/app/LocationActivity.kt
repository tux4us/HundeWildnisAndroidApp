package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityLocationBinding
import java.util.Locale

class LocationActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MIN_TIME_BETWEEN_UPDATES = 5000L // 5 Sekunden
        private const val MIN_DISTANCE_CHANGE = 10f // 10 Meter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Standort"
            setDisplayHomeAsUpEnabled(true)
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        binding.shareButton.setOnClickListener {
            shareLocation()
        }

        checkLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLocation()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    binding.statusText.text = "Standortberechtigung benötigt"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Standortberechtigung wurde verweigert",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = "Standort wird ermittelt..."

        // Prüfe verfügbare Provider
        val providers = locationManager.allProviders
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            binding.statusText.text = "GPS und Netzwerk sind deaktiviert"
            binding.progressBar.visibility = View.GONE
            Toast.makeText(
                this,
                "Bitte aktiviere GPS oder mobiles Netzwerk",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Versuche zuerst die letzte bekannte Position zu holen
        var bestLocation: Location? = null

        if (gpsEnabled) {
            val lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            bestLocation = lastGpsLocation
        }

        if (networkEnabled) {
            val lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (bestLocation == null ||
                (lastNetworkLocation != null && lastNetworkLocation.accuracy < bestLocation.accuracy)) {
                bestLocation = lastNetworkLocation
            }
        }

        if (bestLocation != null && isLocationRecent(bestLocation)) {
            updateLocationUI(bestLocation)
        } else {
            // Fordere neue Position an
            requestLocationUpdates()
        }
    }

    private fun isLocationRecent(location: Location): Boolean {
        val maxAge = 60000L // 1 Minute
        return System.currentTimeMillis() - location.time < maxAge
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        isRequestingUpdates = true

        // Nutze GPS wenn verfügbar (genauer)
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                this
            )
        }

        // Nutze Netzwerk als Fallback (schneller)
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                this
            )
        }

        binding.statusText.text = "Warte auf GPS-Signal..."
    }

    private fun stopLocationUpdates() {
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onLocationChanged(location: Location) {
        updateLocationUI(location)
        // Stoppe Updates nach erfolgreicher Positionsbestimmung
        stopLocationUpdates()
    }

    override fun onProviderEnabled(provider: String) {
        Toast.makeText(this, "$provider aktiviert", Toast.LENGTH_SHORT).show()
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "$provider deaktiviert", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Deprecated, aber muss für Kompatibilität implementiert werden
    }

    private fun updateLocationUI(location: Location) {
        currentLocation = location

        binding.progressBar.visibility = View.GONE
        binding.statusText.text = "Standort gefunden (${location.provider})"

        binding.latitudeText.text = String.format(
            Locale.GERMAN,
            "Breitengrad: %.6f°",
            location.latitude
        )

        binding.longitudeText.text = String.format(
            Locale.GERMAN,
            "Längengrad: %.6f°",
            location.longitude
        )

        binding.accuracyText.text = String.format(
            Locale.GERMAN,
            "± %.0f Meter",
            location.accuracy
        )

        binding.shareButton.isEnabled = true
    }

    private fun shareLocation() {
        currentLocation?.let { location ->
            val latitude = location.latitude
            val longitude = location.longitude

            // Geo URI für verschiedene Apps
            val geoUri = "geo:$latitude,$longitude?q=$latitude,$longitude(Mein Standort)"

            // Text für Teilen
            val shareText = """
                Mein Standort:
                
                Koordinaten: $latitude, $longitude
                
                Google Maps: https://maps.google.com/?q=$latitude,$longitude
                OpenStreetMap: https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude&zoom=15
            """.trimIndent()

            // Share Intent erstellen
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Mein Standort")
            }

            // Geo Intent für Navi-Apps
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))

            // Chooser erstellen
            val chooserIntent = Intent.createChooser(shareIntent, "Standort teilen über...")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(geoIntent))

            startActivity(chooserIntent)
        } ?: run {
            Toast.makeText(this, "Kein Standort verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}