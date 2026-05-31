package pl.syntaxdevteam.medstock.core.barcode

import android.app.Activity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MedicationPackageScanner(private val activity: Activity) {

    fun start(
        onPackageCodeScanned: (String) -> Unit,
        onEmptyResult: () -> Unit,
        onCanceled: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_QR_CODE,
            )
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(activity, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val code = PackageCodeNormalizer.normalizeScannerValues(
                    rawValue = barcode.rawValue,
                    displayValue = barcode.displayValue,
                    rawBytes = barcode.rawBytes,
                )
                if (code.isBlank()) {
                    onEmptyResult()
                } else {
                    onPackageCodeScanned(code)
                }
            }
            .addOnCanceledListener {
                onCanceled()
            }
            .addOnFailureListener {
                onFailure()
            }
    }
}
