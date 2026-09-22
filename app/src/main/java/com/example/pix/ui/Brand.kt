package com.example.pix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pix.R

@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    Image(
        painterResource(
            if (MaterialTheme.colorScheme.background.luminance() < .5f)
                R.drawable.brand_wordmark_dark
            else R.drawable.brand_wordmark_light
        ),
        "p©ix",
        modifier.height(60.dp).width(112.dp),
    )
}
