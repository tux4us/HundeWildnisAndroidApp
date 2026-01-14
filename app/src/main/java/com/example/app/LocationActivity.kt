package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.hundewildnis.app.databinding.ActivityLocationBinding
import java.util.Locale

class LocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.shareButton.setOnClickListener {
            shareLocation()
        }

        checkLocationPermission()
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

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                updateLocationUI(it)
            } ?: run {
                // Wenn keine letzte Position verfügbar, fordere neue an
                requestNewLocation()
            }
        }
    }

    private fun requestNewLocation() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).apply {
            setMinUpdateIntervalMillis(5000)
            setMaxUpdates(1)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationUI(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    private fun updateLocationUI(location: Location) {
        currentLocation = location

        binding.progressBar.visibility = View.GONE
        binding.statusText.text = "Standort gefunden"

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