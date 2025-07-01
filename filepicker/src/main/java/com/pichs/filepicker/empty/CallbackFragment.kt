package com.pichs.filepicker.empty

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class CallbackFragment : Fragment() {

    private var launcher: ActivityResultLauncher<Intent>? = null

    var onResult: ((resultCode: Int, data: Intent?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            onResult?.invoke(result.resultCode, result.data)
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    fun launch(intent: Intent) {
        launcher?.launch(intent)
    }
}