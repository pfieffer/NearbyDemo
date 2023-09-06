package ai.app.nearbydemo

import ai.app.nearbydemo.ui.theme.NearbyDemoTheme
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy


class MainActivity : ComponentActivity() {
    lateinit var nearbyConnectionsClient: ConnectionsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nearbyConnectionsClient = Nearby.getConnectionsClient(this)
        setContent {
            NearbyDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = {
                            startAdvertising()
                            startDiscovery()
                        }) {
                            Text(text = "Advertise & Discover")
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                    }
                }
            }
        }
    }


    private fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions =
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        nearbyConnectionsClient
            .startAdvertising(
                LOCAL_USER_NAME,
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions
            )
            .addOnSuccessListener {
                Log.d("Advertising", " has started")
            }
            .addOnFailureListener { e: Exception ->
                e.printStackTrace()
            }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        nearbyConnectionsClient
            .startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("Endpoint Discovery", " $endpointId is found")
                    // An endpoint was found. We request a connection to it.
                    nearbyConnectionsClient
                        .requestConnection(LOCAL_USER_NAME, endpointId, connectionLifecycleCallback)
                        .addOnSuccessListener {
                            Log.d("Endpoint Connection Request", " successful")
                        }
                        .addOnFailureListener { e: Exception ->
                            e.printStackTrace()
                        }
                }

                override fun onEndpointLost(endpointId: String) {
                    // A previously discovered endpoint has gone away.
                    Log.d("Endpoint Discovery", " previously discovered $endpointId has gone away")
                }
            }, discoveryOptions)
            .addOnSuccessListener {
                Log.d("Discovery", " has started")
            }
            .addOnFailureListener { e: Exception ->
                e.printStackTrace()
            }
    }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Accept connection to " + connectionInfo.endpointName)
                    .setMessage("Confirm the code matches on both devices: " + connectionInfo.authenticationDigits)
                    .setPositiveButton(
                        "Accept"
                    ) { _: DialogInterface?, _: Int ->  // The user confirmed, so we can accept the connection.
                        nearbyConnectionsClient
                            .acceptConnection(endpointId, payloadCallback)
                    }
                    .setNegativeButton(
                        android.R.string.cancel
                    ) { _: DialogInterface?, _: Int ->  // The user canceled, so we should reject the connection.
                        nearbyConnectionsClient.rejectConnection(endpointId)
                    }
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show()
                // Automatically accept the connection on both sides.
//                nearbyConnectionsClient.acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        Log.d("Connection Lifecycle", " Connection Status is OK")
                        val bytesPayload = Payload.fromBytes(byteArrayOf(0xa, 0xb, 0xc, 0xd))
                        nearbyConnectionsClient.sendPayload(endpointId, bytesPayload)
                    }

                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        Log.d("Connection Lifecycle", " Connection Status is REJECTED")
                    }

                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        Log.d("Connection Lifecycle", " Connection Status is Error")
                    }

                    else -> {
                        Log.d("Connection Lifecycle", " Connection Status is unknown")
                    }
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
                Log.d("Connection Lifecycle", " Connection with $endpointId is Disconnected")
            }
        }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This always gets the full data of the payload. Is null if it's not a BYTES payload.
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes = payload.asBytes()
                Log.d("Payload", " Bytes received: $receivedBytes")
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            payloadTransferUpdate: PayloadTransferUpdate
        ) {
            Log.d(
                "PayloadTransferUpdate: ",
                "${((payloadTransferUpdate.bytesTransferred / payloadTransferUpdate.totalBytes) * 100)}%"
            )
        }
    }

    override fun onStop() {
        super.onStop()
        nearbyConnectionsClient.stopAdvertising()
        nearbyConnectionsClient.stopDiscovery()
        nearbyConnectionsClient.stopAllEndpoints()
    }

    companion object {
        val STRATEGY = Strategy.P2P_CLUSTER
        val SERVICE_ID: String =
            "com.example.nearbyscratch"
//            "ai.app.nearbydemo.service_id"
        val LOCAL_USER_NAME = Build.MANUFACTURER + " " + Build.MODEL
    }
}

