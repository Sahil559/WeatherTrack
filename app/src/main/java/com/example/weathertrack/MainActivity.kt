package com.example.weathertrack

import android.content.Intent
import android.os.Bundle
import android.location.LocationManager
import android.widget.Toast
import android.provider.Settings
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.location.Location
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.example.weathertrack.Data.dataClass
import com.google.android.gms.location.*
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.*
import java.util.*
import android.*
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import com.example.weathertrack.R.layout.*
import android.widget.TextView.*
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.example.weathertrack.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import com.example.weathertrack.databinding.ActivityBinding

@Suppress("DEPRECATION", "NAME_SHADOWING")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBinding

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(API.PREFERENCE_NAME, Context.MODE_PRIVATE)

        binding = ActivityBinding.inflate(layoutInflater)
        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Please turn it on your location.",
                Toast.LENGTH_SHORT
            ).show()


            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            val mLastLocation: Location? = locationResult.lastLocation
            if (mLastLocation != null) {
                mLatitude = mLastLocation.latitude
            }
            Log.e("Current Latitude", "$mLatitude")
            if (mLastLocation != null) {
                mLongitude = mLastLocation.longitude
            }
            Log.e("Current Longitude", "$mLongitude")

            getLocationWeatherDetails()
        }
    }

    private fun getLocationWeatherDetails() {

        if (API.isNetworkAvailable(this@MainActivity)) {
            val retrofit: Retrofit = Retrofit.Builder()

                .baseUrl(API.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: APIService =
                retrofit.create(APIService::class.java)

            val listCall: Call<dataClass> = service.getWeather(
                mLatitude, mLongitude, API.METRIC_UNIT, API.APP_ID
            )

            showCustomProgressDialog()


            listCall.enqueue(object : Callback<dataClass> {


                override fun onResponse(call: Call<dataClass>, response: Response<dataClass>) {

                    if (response.isSuccessful) {

                        hideProgressDialog()

                        val weatherList: dataClass? = response.body()
                        setupUI()
                        Log.i("Response Result", "$weatherList")


                        val weatherResponseJsonString = Gson().toJson(weatherList)

                        val editor = mSharedPreferences.edit()
                        editor.putString(API.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()

                    } else {
                        // If the response is not success then we check the response code.
                        when (response.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<dataClass>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Error", t.message.toString())
                }

            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("ResourceType")
    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(progress_dialog)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val weatherResponseJsonString =
            mSharedPreferences.getString(API.WEATHER_RESPONSE_DATA, "")


        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList =
                Gson().fromJson(weatherResponseJsonString, dataClass::class.java)
            for (z in weatherList.weather.indices) {
                Log.i("NAME", weatherList.weather[z].main)

                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.tempMin.toString() + " min"
                binding.tvMax.text = weatherList.main.tempMax.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())

                when (weatherList.weather[z].icon) {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                }
            }

        }
    }
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun getUnit(value: String): String {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.root, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                getLocationWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


}



