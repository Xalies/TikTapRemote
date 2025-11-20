package com.xalies.tiktapremote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class IndicatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val x = intent.getIntExtra("x", 0)
        val y = intent.getIntExtra("y", 0)
        val indicatorSize = 40

        setContent {
            Box(
                modifier = Modifier
                    .offset(
                        x = (x - (indicatorSize / 2)).dp,
                        y = (y - (indicatorSize / 2)).dp
                    )
                    .size(indicatorSize.dp)
                    .background(Color.Red.copy(alpha = 0.5f), shape = CircleShape)
            )
        }

        // Finish the activity after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 200)
    }
}