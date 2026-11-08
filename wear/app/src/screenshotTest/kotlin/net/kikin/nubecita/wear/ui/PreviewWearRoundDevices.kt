package net.kikin.nubecita.wear.ui

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * The small (192dp) and large round watches, the two ends of the round screens
 * the Wear quality guidelines require. `showSystemUi` renders the device frame.
 */
@Preview(name = "Small round", device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Large round", device = Devices.WEAR_OS_LARGE_ROUND, showSystemUi = true, showBackground = true, backgroundColor = 0xFF000000)
annotation class PreviewWearRoundDevices
