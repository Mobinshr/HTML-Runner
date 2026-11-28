package com.mobin.htmlrunner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class NativeHomeActivity : ComponentActivity() {
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("html_runner", MODE_PRIVATE).edit()
                    .putString("project_tree_uri", uri.toString()).apply()
                launchRunner()
            } catch (_: SecurityException) {
                // The launcher remains visible so the user can choose another folder.
            }
        }
    }

    private val htmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            launchRunner(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF71C7FF),
                background = Color(0xFF071321),
                surface = Color(0xFF10263A)
            )) {
                NativeRunnerHome(
                    hasSavedProject = getSharedPreferences("html_runner", MODE_PRIVATE)
                        .contains("project_tree_uri"),
                    onChooseFolder = { folderPicker.launch(null) },
                    onChooseHtml = { htmlPicker.launch(arrayOf("text/html", "application/xhtml+xml")) },
                    onContinue = { launchRunner() }
                )
            }
        }
    }

    private fun launchRunner(file: Uri? = null) {
        val intent = Intent(this, MainActivity::class.java)
        if (file != null) {
            intent.action = Intent.ACTION_VIEW
            intent.setDataAndType(file, "text/html")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}

@Composable
private fun NativeRunnerHome(
    hasSavedProject: Boolean,
    onChooseFolder: () -> Unit,
    onChooseHtml: () -> Unit,
    onContinue: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF071321)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF102B45), Color(0xFF071321))))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF173650))
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF8AD4FF))
                    Spacer(Modifier.height(12.dp))
                    Text("HTML Runner", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "پروژه‌های HTML، CSS و JavaScript را مستقیماً روی گوشی اجرا کن.",
                        color = Color(0xFFD0DEEA),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AppUpdateControl()
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onChooseFolder,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1686C9))
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Text("  انتخاب پوشهٔ پروژه", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onChooseHtml,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Text("  اجرای یک فایل HTML")
            }
            if (hasSavedProject) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  ادامهٔ آخرین پروژه")
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "موتور اجرای صفحات از WebView اندروید استفاده می‌کند؛ انتخاب پروژه و محیط برنامه Native است.",
                color = Color(0xFF93AFC4),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
