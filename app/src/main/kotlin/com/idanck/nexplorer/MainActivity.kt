package com.idanck.nexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.idanck.nexplorer.ui.catalog.ThemeCatalogScreen
import com.idanck.nexplorer.ui.theme.NexplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NexplorerTheme {
                // זמני: קטלוג הטוקנים. יוחלף במסך הראשי כשיתחיל המימוש.
                ThemeCatalogScreen()
            }
        }
    }
}
