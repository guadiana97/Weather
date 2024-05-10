package com.lab4.weather

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.lab4.weather.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        // Get API Key from OpenWeather
        private const val API_KEY = "6e6627e4c51d021a10eb57b2c700d81f"
        private const val CITY_NAME_URL = "https://api.openweathermap.org/data/2.5/weather?q="
        private const val GEO_COORDINATES_URL = "https://api.openweathermap.org/data/2.5/weather?lat="
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    // Sound to play when clicking location button
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializing the media player
        mediaPlayer = MediaPlayer.create(this, R.raw.japanese_wind_chimes)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)



        // Setting the button click listener to extract the current location
        binding.fab.setOnClickListener{
            getLocation()
            onClick(view = null)
        }

        binding.root.setOnRefreshListener {
            refreshData()
        }

    }

    // This method ensures that upon return to app the most up-to-date forecast is displayed by refrshing the data and also activates the sound play
    override fun onResume() {
        super.onResume()

        refreshData()
    }

    override fun onClick(view: View?) {
        // Play the sound
        mediaPlayer?.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            // The following enables the refresh response to update the weather
            R.id.refresh -> refreshData()
            R.id.change_city -> showInputDialog()

        }
        return super.onOptionsItemSelected(item)
    }

    // The following method is for requesting permission to get current location
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation()
        else if (requestCode == 1) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(applicationContext,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val url = GEO_COORDINATES_URL + location.latitude + "&lon=" + location.longitude
                    updateWeatherData(url)
                    sharedPreferences.edit().apply {
                        putString("location", "currentLocation")
                        apply()
                    }
                }
            }
        }
    }

    private fun updateWeatherData(url: String) {
        object : Thread() {
            override fun run() {
                val jsonObject = getJSON(url)
                runOnUiThread {
                    if (jsonObject != null) renderWeather(jsonObject)
                    else Toast.makeText(this@MainActivity, getString(R.string.data_not_found), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getJSON(url: String): JSONObject? {
        try {

            //Units here will be set to imperial (Fahrenheit)
            val con = URL("$url&appid=$API_KEY&units=imperial").openConnection() as HttpURLConnection
            con.apply {
                doOutput = true
                connect()
            }

            val inputStream = con.inputStream
            val br = BufferedReader(InputStreamReader(inputStream!!))
            var line: String?
            val buffer = StringBuffer()
            while (br.readLine().also { line = it } != null) buffer.append(line + "\n")
            inputStream.close()
            con.disconnect()

            val jsonObject = JSONObject(buffer.toString())

            return if (jsonObject.getInt("cod") != 200) null
            else jsonObject
        } catch (_: Throwable) {
            return null
        }
    }

    private fun renderWeather(json: JSONObject) {
        try {
            val city = json.getString("name").uppercase(Locale.US)
            val country = json.getJSONObject("sys").getString("country")
            binding.txtCity.text = resources.getString(R.string.city_field, city, country)

            val weatherDetails = json.optJSONArray("weather")?.getJSONObject(0)
            val main = json.getJSONObject("main")
            val description = weatherDetails?.getString("description")
            val humidity = main.getString("humidity")
            val pressure = main.getString("pressure")
            binding.txtDetails.text = resources.getString(R.string.details_field, description, humidity, pressure)

            // Icons for the different weathers
            val iconID = weatherDetails?.getString("icon") ?: "03d"
            val url = "https://openweathermap.org/img/wn/$iconID@2x.png"

            Glide.with(this)
                .load(url)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(binding.imgWeatherIcon)

            val temperature = main.getDouble("temp")
            binding.txtTemperature.text = resources.getString(R.string.temperature_field, temperature)

            val df = DateFormat.getDateTimeInstance()
            val lastUpdated = df.format(Date(json.getLong("dt") * 1000))
            binding.txtUpdated.text = resources.getString(R.string.updated_field, lastUpdated)
        } catch (_: Exception) { }
    }

    private fun showInputDialog() {
        val input = EditText(this@MainActivity)
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.change_city))
            setView(input)
            setPositiveButton(getString(R.string.go)) { _, _ ->
                val city = input.text.toString()
                updateWeatherData("$CITY_NAME_URL$city")
                sharedPreferences.edit().apply {
                    putString("location", city)
                    apply()
                }
            }
            show()
        }
    }

    // Ensures widget displays the refreshing symbol upon doing the swiping gesture
    private fun refreshData() {
        binding.root.isRefreshing = true
        when (val location = sharedPreferences.getString("location", null)) {
            null, "currentLocation" -> getLocation()
            else -> updateWeatherData("$CITY_NAME_URL$location")
        }
        binding.root.isRefreshing = false
    }

}

