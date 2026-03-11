package net.stauende.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.color.MaterialColors
import net.stauende.app.databinding.ActivityMainBinding

/**
 * Main screen of the StauEnde app.
 *
 * Lets the user enter an origin and destination, fetches traffic-aware routes
 * via [RouteViewModel], displays them on a [GoogleMap], and lists optimal
 * departure windows to avoid common daily jams.
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var googleMap: GoogleMap? = null

    private val viewModel: RouteViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val calculator = RouteCalculator(apiKey = BuildConfig.MAPS_API_KEY)
                return RouteViewModel(calculator) as T
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        enableMyLocation(granted)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupUi()
        observeViewModel()
    }

    // ------------------------------------------------------------------
    // Map
    // ------------------------------------------------------------------

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = true
        }
        map.setTrafficEnabled(true)
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation(true)
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @Suppress("MissingPermission")
    private fun enableMyLocation(granted: Boolean) {
        googleMap?.isMyLocationEnabled = granted
    }

    // ------------------------------------------------------------------
    // UI setup
    // ------------------------------------------------------------------

    private fun setupUi() {
        binding.buttonCalculate.setOnClickListener {
            val origin = binding.editOrigin.text?.toString() ?: ""
            val destination = binding.editDestination.text?.toString() ?: ""
            viewModel.calculateRoutes(origin, destination)
        }
    }

    // ------------------------------------------------------------------
    // ViewModel observation
    // ------------------------------------------------------------------

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is RouteUiState.Idle -> showIdle()
                is RouteUiState.Loading -> showLoading()
                is RouteUiState.RoutesLoaded -> showRoutes(state.routes)
                is RouteUiState.Error -> showError(state.message)
            }
        }

        viewModel.departureWindows.observe(this) { windows ->
            if (windows.isNotEmpty()) {
                val best = windows.firstOrNull()
                binding.textDepartureSuggestion.text = if (best != null) {
                    getString(
                        R.string.departure_suggestion,
                        best.label,
                        formatDuration(best.estimatedDurationSeconds)
                    )
                } else {
                    ""
                }
                binding.textDepartureSuggestion.visibility =
                    if (best != null) View.VISIBLE else View.GONE
            }
        }

        viewModel.jamWindows.observe(this) { jams ->
            if (jams.isNotEmpty()) {
                binding.textJamSummary.text = buildJamSummary(jams)
                binding.textJamSummary.visibility = View.VISIBLE
            } else {
                binding.textJamSummary.visibility = View.GONE
            }
        }
    }

    // ------------------------------------------------------------------
    // UI state helpers
    // ------------------------------------------------------------------

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.GONE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.cardRouteInfo.visibility = View.GONE
    }

    private fun showRoutes(routes: List<RouteOption>) {
        binding.progressBar.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.VISIBLE

        val best = routes.minByOrNull { it.durationInTrafficSeconds } ?: return

        binding.textRouteDistance.text = formatDistance(best.distanceMeters)
        binding.textRouteDuration.text = formatDuration(best.durationInTrafficSeconds)
        binding.textTrafficStatus.text = buildTrafficStatusText(best)
        binding.textRouteAddress.text = getString(
            R.string.route_address_format,
            best.startAddress,
            best.endAddress
        )

        // Draw polyline markers on the map for all returned routes.
        googleMap?.let { map ->
            map.clear()
            routes.forEachIndexed { index, route ->
                drawRouteOnMap(map, route, isPrimary = index == 0)
            }
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------
    // Map drawing
    // ------------------------------------------------------------------

    private fun drawRouteOnMap(map: GoogleMap, route: RouteOption, isPrimary: Boolean) {
        // For a production app, decode the encoded polyline from the API response.
        // Here we place start / end markers using the addresses as a visual cue.
        val color = if (isPrimary) {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        } else {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary)
        }

        // Geocode the addresses to LatLng for the demo. In production, use the
        // location endpoints returned by the Directions API (start_location / end_location).
        val markerHue = if (isPrimary) BitmapDescriptorFactory.HUE_RED
        else BitmapDescriptorFactory.HUE_AZURE

        map.addMarker(
            MarkerOptions()
                .title(route.startAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
                .position(LatLng(0.0, 0.0)) // placeholder — real apps use leg.startLocation
        )
        map.addMarker(
            MarkerOptions()
                .title(route.endAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
                .position(LatLng(0.0, 0.0)) // placeholder
        )
    }

    // ------------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------------

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
            getString(R.string.duration_hours_minutes, hours, minutes)
        } else {
            getString(R.string.duration_minutes, minutes)
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            getString(R.string.distance_km, meters / 1000.0)
        } else {
            getString(R.string.distance_m, meters)
        }
    }

    private fun buildTrafficStatusText(route: RouteOption): String {
        return if (route.hasSignificantTraffic) {
            val delayMin = route.trafficDelaySeconds / 60
            getString(R.string.traffic_delay_minutes, delayMin)
        } else {
            getString(R.string.traffic_clear)
        }
    }

    private fun buildJamSummary(jams: List<JamWindow>): String {
        val sb = StringBuilder(getString(R.string.jam_summary_header))
        jams.filter { it.trafficLevel == TrafficLevel.HIGH || it.trafficLevel == TrafficLevel.SEVERE }
            .take(3)
            .forEach { jam ->
                sb.append("\n  • ${jam.label}  (+${jam.delayPercent} %)")
            }
        return sb.toString()
    }
}
