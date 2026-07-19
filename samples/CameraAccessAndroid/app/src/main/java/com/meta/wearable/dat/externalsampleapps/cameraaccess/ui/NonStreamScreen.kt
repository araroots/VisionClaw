/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// NonStreamScreen - DAT Device Selection and Setup

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonStreamScreen(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val gettingStartedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  var dropdownExpanded by remember { mutableStateOf(false) }
  val isDisconnectEnabled = uiState.registrationState is RegistrationState.Registered
  val activity = LocalActivity.current
  val context = LocalContext.current
  // Hoisted out of the onClick lambda below -- tr() is @Composable and can't be called from a
  // plain event-handler lambda, only from composable scope.
  val activityNotAvailableMessage = tr("Activity não disponível", "Activity not available")

  MaterialTheme(colorScheme = darkColorScheme()) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(all = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
      // Language toggle (top-left) -- this is the screen most users actually land on day to
      // day (glasses already registered), so the toggle needs to live here too, not just on
      // the one-time HomeScreen registration flow.
      LanguageToggle(
          modifier = Modifier.align(Alignment.TopStart).systemBarsPadding(),
      )

      // Top bar: settings + disconnect
      Row(
          modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(onClick = { viewModel.showHistory() }) {
          Icon(
              imageVector = Icons.Default.History,
              contentDescription = tr("Histórico de Conversas", "Conversation History"),
              tint = Color.White,
              modifier = Modifier.size(28.dp),
          )
        }

        IconButton(onClick = { viewModel.showSettings() }) {
          Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = tr("Configurações", "Settings"),
              tint = Color.White,
              modifier = Modifier.size(28.dp),
          )
        }

        Box {
          IconButton(onClick = { dropdownExpanded = true }) {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = tr("Ícone de Desconectar", "DisconnectIcon"),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
          }

          DropdownMenu(
              expanded = dropdownExpanded,
              onDismissRequest = { dropdownExpanded = false },
          ) {
            DropdownMenuItem(
                text = {
                  Text(
                      tr("Desconectar", "Disconnect"),
                      color = if (isDisconnectEnabled) AppColor.Red else Color.Gray,
                  )
                },
                enabled = isDisconnectEnabled,
                onClick = {
                  activity?.let { viewModel.startUnregistration(it) }
                      ?: Toast.makeText(context, activityNotAvailableMessage, Toast.LENGTH_SHORT).show()
                  dropdownExpanded = false
                },
                modifier = Modifier.height(30.dp),
            )
          }
        }
      }

      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
            painter = painterResource(id = R.drawable.camera_access_icon),
            contentDescription = tr("Ícone do Camera Access", "Camera Access icon"),
            tint = Color.White,
            modifier = Modifier.size(80.dp * LocalDensity.current.density),
        )
        Text(
            text = tr("Transmita a Câmera dos Seus Óculos", "Stream Your Glasses Camera"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.White,
        )
        Text(
            text = tr(
                "Toque no botão Iniciar transmissão para transmitir vídeo dos seus óculos, ou use o botão de câmera para tirar uma foto pelos seus óculos.",
                "Tap the Start streaming button to stream video from your glasses or use the camera button to take a photo from your glasses.",
            ),
            textAlign = TextAlign.Center,
            color = Color.White,
        )
      }

      Column(
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!uiState.hasActiveDevice) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(bottom = 4.dp),
          ) {
            Icon(
                painter = painterResource(id = R.drawable.hourglass_icon),
                contentDescription = tr("Aguardando dispositivo", "Waiting for device"),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = tr("Aguardando um dispositivo ativo", "Waiting for an active device"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
          }
        }

        // Start Streaming Button (glasses)
        SwitchButton(
            label = tr("Iniciar transmissão", "Start streaming"),
            onClick = { viewModel.navigateToStreaming(onRequestWearablesPermission) },
            enabled = uiState.hasActiveDevice,
        )

        // Start on Phone Button
        SwitchButton(
            label = tr("Iniciar no Celular", "Start on Phone"),
            onClick = { viewModel.navigateToPhoneMode() },
        )
      }

      // Getting Started Sheet
      if (uiState.isGettingStartedSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideGettingStartedSheet() },
            sheetState = gettingStartedSheetState,
        ) {
          GettingStartedSheetContent(
              onContinue = {
                scope.launch {
                  gettingStartedSheetState.hide()
                  viewModel.hideGettingStartedSheet()
                }
              }
          )
        }
      }
    }
  }
}

@Composable
private fun GettingStartedSheetContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
        text = tr("Primeiros passos", "Getting started"),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp).padding(bottom = 16.dp),
    ) {
      TipItem(
          iconResId = R.drawable.video_icon,
          text = tr(
              "Primeiro, o Camera Access precisa de permissão para usar a câmera dos seus óculos.",
              "First, Camera Access needs permission to use your glasses camera.",
          ),
      )
      TipItem(
          iconResId = R.drawable.tap_icon,
          text = tr("Capture fotos tocando no botão da câmera.", "Capture photos by tapping the camera button."),
      )
      TipItem(
          iconResId = R.drawable.smart_glasses_icon,
          text = tr(
              "O LED de captura avisa outras pessoas quando você está capturando conteúdo ou ao vivo.",
              "The capture LED lets others know when you're capturing content or going live.",
          ),
      )
    }

    SwitchButton(
        label = tr("Continuar", "Continue"),
        onClick = onContinue,
        modifier = Modifier.navigationBarsPadding(),
    )
  }
}

@Composable
private fun TipItem(iconResId: Int, text: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = tr("Ícone de dica inicial", "Getting started tip icon"),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(text = text)
  }
}
