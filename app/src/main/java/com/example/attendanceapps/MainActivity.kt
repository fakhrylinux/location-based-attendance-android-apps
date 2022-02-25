package com.example.attendanceapps

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.skyfishjy.library.RippleBackground
import java.lang.Math.toRadians
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val ID_LOCATION_PERMISSION = 0
    }

    private lateinit var rippleBackground: RippleBackground
    private lateinit var tvScanning: TextView
    private lateinit var tvCheckInSuccess: TextView
    private lateinit var fabCheckIn: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rippleBackground = findViewById(R.id.rippleBackground)
        tvScanning = findViewById(R.id.tvScanning)
        tvCheckInSuccess = findViewById(R.id.tvCheckInSuccess)
        fabCheckIn = findViewById(R.id.fabCheckIn)

        checkPermissionLocation()
        onClick()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ID_LOCATION_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED || grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Berhasil diizinkan", Toast.LENGTH_SHORT).show()
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Please turn on your location", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            } else {
                Toast.makeText(this, "Gagal diizinkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionLocation() {
        if (checkPermission()) {
            if (!isLocationEnabled()) {
                Toast.makeText(this, "Please turn on your location", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        } else {
            requestPermission()
        }
    }

    private fun checkPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return true
        }
        return false
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                ACCESS_COARSE_LOCATION,
                ACCESS_FINE_LOCATION
            ),
            ID_LOCATION_PERMISSION
        )
    }

    private fun onClick() {
        fabCheckIn.setOnClickListener {
            loadScanLocation()
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    getLastLocation()
                },
                4000
            )
        }
    }

    private fun loadScanLocation() {
        rippleBackground.startRippleAnimation()
        tvScanning.visibility = View.VISIBLE
        tvCheckInSuccess.visibility = View.GONE
    }

    private fun stopScanLocation() {
        rippleBackground.stopRippleAnimation()
        tvScanning.visibility = View.GONE
    }

    private fun getLastLocation() {
        if (checkPermission()) {
            if (isLocationEnabled()) {
                LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                    val currentLat = location.latitude
                    val currentLong = location.longitude

                    val distance: Double = calculateDistance(
                        currentLat,
                        currentLong,
                        getAddresses()[0].latitude,
                        getAddresses()[0].longitude
                    ) * 1000

                    if (distance < 10.0) {
                        showDialogForm()
                    } else {
                        tvCheckInSuccess.visibility = View.VISIBLE
                        tvCheckInSuccess.text = getString(R.string.out_of_range)
                    }
                    stopScanLocation()
                }
            } else {
                Toast.makeText(this, "Please turn on your location", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        } else {
            requestPermission()
        }
    }

    private fun showDialogForm() {
        val dialogForm: View = LayoutInflater.from(this).inflate(R.layout.layout_dialog_form, null)
        AlertDialog.Builder(this)
            .setView(dialogForm)
            .setCancelable(false)
            .setPositiveButton("Submit") { dialog, _ ->
                val name = dialogForm.findViewById<EditText>(R.id.etName).text.toString()
                inputDataToFireBase(name)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun inputDataToFireBase(name: String) {
        val user = User(name, getCurrentDate())
        val database = FirebaseDatabase.getInstance()
        val attendanceRef = database.getReference("log_attendance")
        attendanceRef.child(name).setValue(user)
            .addOnSuccessListener {
                tvCheckInSuccess.visibility = View.VISIBLE
                tvCheckInSuccess.text = getString(R.string.check_in_success)
            }
            .addOnFailureListener {
                Toast.makeText(this, "${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentDate(): String {
        val currentTime = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(currentTime)
    }

    private fun getAddresses(): List<Address> {
        val destinationPlace = "SPBU Maliaro"
        val geocode = Geocoder(this, Locale.getDefault())
        return geocode.getFromLocationName(destinationPlace, 100)
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6372.8 // in kilometers
        val radiansLat1 = toRadians(lat1)
        val radiansLat2 = toRadians(lat2)
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        return 2 * r * asin(
            sqrt(
                sin(dLat / 2).pow(2.0)
                        + sin(dLon / 2).pow(2.0)
                        * cos(radiansLat1)
                        * cos(radiansLat2)
            )
        )
    }
}
