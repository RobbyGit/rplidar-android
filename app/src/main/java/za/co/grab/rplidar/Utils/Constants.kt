package za.co.grab.rplidar.Utils

import java.time.format.DateTimeFormatter
import java.util.Locale

object Constants {
    const val TAG = "RPLIDAR_PARSER"

    const val ACTION_USB_PERMISSION = "za.co.grab.rplidar.USB_PERMISSION"
    const val BAUDRATE: Int = 115200

    val FMT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    val FMT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

}

