package za.co.grab.rplidar

import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import za.co.grab.rplidar.Utils.Constants.ACTION_USB_PERMISSION
import za.co.grab.rplidar.Utils.Constants.BAUDRATE
import za.co.grab.rplidar.Utils.Constants.TAG
import java.nio.charset.Charset
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var usbManager: UsbManager
    private var usbSerialDevice: UsbSerialDevice? = null

    private lateinit var radarMapView: RadarMapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Log.w(TAG, "Starting Rplidar")

        radarMapView = findViewById(R.id.radarMapView)

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        val vendorId = 4292
        val productId = 60000

        //val vendorId = 12346
        //val productId = 4097


        val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        usbDevice?.let {
            val vendorId = usbDevice.vendorId
            val productId = usbDevice.productId

            // Now you can use vendorId and productId as needed
            Log.w(TAG, "Device Vendor ID: $vendorId")
            Log.w(TAG, "Device Product ID: $productId")
        }

        if (usbDevice != null) {
            if (usbDevice.vendorId == vendorId && usbDevice.productId == productId) {
                if (usbManager.hasPermission(usbDevice)) {
                    openSerialPort(usbDevice)
                    Log.w(TAG, "ok permissions")
                } else {
                    Log.e(TAG, "No permissions")
                    val permissionIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    usbManager.requestPermission(usbDevice, permissionIntent)
                }
            } else {
                Log.e(TAG, "USB device not found")
            }
        } else {
            Log.e(TAG, "No USB device found")
        }
    }

    private fun openSerialPort(usbDevice: UsbDevice) {
        usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbManager.openDevice(usbDevice))
        usbSerialDevice?.let { usbSerial ->
            if (usbSerial.open()) {
                Log.w(TAG, "Setting USB serial port values")
                usbSerial.setBaudRate(BAUDRATE)
                usbSerial.setDataBits(UsbSerialInterface.DATA_BITS_8)
                usbSerial.setStopBits(UsbSerialInterface.STOP_BITS_1)
                usbSerial.setParity(UsbSerialInterface.PARITY_NONE)
                usbSerial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

                // Keep DTR false (Low). On A1 adapters, keeping DTR low keeps the motor powered!
                usbSerial.setDTR(false)

                Log.w(TAG, "USB set and ready. Sending Start Scan Request...")

                val startScanCmd = byteArrayOf(0xA5.toByte(), 0x20.toByte())
                usbSerial.write(startScanCmd)

                usbSerial.read { data ->
                    if (data != null && data.isNotEmpty()) {
                        processRawBytes(data)
                    }
                }
            }
        }
    }

    fun onFrameReceived(angles: FloatArray, distances: FloatArray) {
        // Because UI changes must happen on the Main Thread, we switch over from our background thread
        runOnUiThread {
            radarMapView.updateData(angles, distances)
        }
    }

//    fun onFrameReceived(angles: FloatArray, distances: FloatArray) {
//        Log.d(TAG, "=== Processing New 360° Scan ===")
//
//        // Loop through every single point captured in this rotation
//        for ((i, element) in angles.withIndex()) {
//            val angleDeg = element
//            val distanceMm = distances[i]
//
//            // 1. Math requires Radians instead of Degrees
//            val angleRad = java.lang.Math.toRadians(angleDeg.toDouble())
//
//            // 2. Convert Polar (Angle/Dist) to Cartesian (X/Y)
//            // X = Distance * cos(Angle)
//            // Y = Distance * sin(Angle)
//            val x = distanceMm * cos(angleRad)
//            val y = distanceMm * sin(angleRad)
//
//            // 3. Let's look at specific directions to understand our environment
//            // We will sample a few key angles to print out clearly
//            if (angleDeg !in 2f..358f) {
//                Log.i(TAG, String.format("WALL FORWARD -> Distance: %.0fmm | Coordinate: (X: %.1f, Y: %.1f)", distanceMm, x, y))
//            } else if (angleDeg in 89.0f..91.0f) {
//                Log.i(TAG, String.format("WALL RIGHT   -> Distance: %.0fmm | Coordinate: (X: %.1f, Y: %.1f)", distanceMm, x, y))
//            }
//        }
//    }

    override fun onDestroy() {
        Log.w(TAG, "APK DESTROYED")
        usbSerialDevice?.close()
        super.onDestroy()
    }

    private external fun processRawBytes(data: ByteArray)

    companion object {
        init {
            // Must match the project name specified in CMakeLists.txt
            System.loadLibrary("rplidar_native")
        }
    }

}