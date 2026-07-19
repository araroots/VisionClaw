/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// HomeScreen - DAT Registration Entry Point

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun HomeScreen(
    viewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val activity = LocalActivity.current
  val context = LocalContext.current

  // Hoisted out of the onClick lambda below -- tr() is @Composable and can't be called from a
  // plain event-handler lambda, only from composable scope.
  val activityNotAvailableMessage = tr("Activity não disponível", "Activity not available")

  Box(modifier = modifier.fillMaxSize()) {
    // Shows both PT and EN with the active one highlighted, rather than a single flag that
    // silently swaps on tap and reads as a passive status badge instead of a control.
    LanguageToggle(
        modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp),
    )

    // Settings gear + history (top-right)
    Row(modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp)) {
      IconButton(onClick = { viewModel.showHistory() }) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = tr("Histórico de Conversas", "Conversation History"),
            tint = Color.Gray,
            modifier = Modifier.size(28.dp),
        )
      }
      IconButton(onClick = { viewModel.showSettings() }) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = tr("Configurações", "Settings"),
            tint = Color.Gray,
            modifier = Modifier.size(28.dp),
        )
      }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(all = 24.dp)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Spacer(modifier = Modifier.weight(1f))
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
            painter = painterResource(id = R.drawable.camera_access_icon),
            contentDescription = tr("Ícone do Camera Access", "Camera Access icon"),
            tint = AppColor.DeepBlue,
            modifier = Modifier.size(80.dp * LocalDensity.current.density),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        ) {
          TipItem(
              iconResId = R.drawable.smart_glasses_icon,
              title = tr("Captura de Vídeo", "Video Capture"),
              text = tr(
                  "Grave vídeos direto dos seus óculos, do seu ponto de vista.",
                  "Record videos directly from your glasses, from your point of view.",
              ),
          )
          TipItem(
              iconResId = R.drawable.sound_icon,
              title = tr("Áudio de Ouvido Aberto", "Open-Ear Audio"),
              text = tr(
                  "Ouça notificações mantendo os ouvidos abertos para o mundo ao seu redor.",
                  "Hear notifications while keeping your ears open to the world around you.",
              ),
          )
          TipItem(
              iconResId = R.drawable.walking_icon,
              title = tr("Aproveite em Movimento", "Enjoy On-the-Go"),
              text = tr(
                  "Fique de mãos livres enquanto segue seu dia. Mova-se livremente, continue conectado.",
                  "Stay hands-free while you move through your day. Move freely, stay connected.",
              ),
          )
        }
      }
      Spacer(modifier = Modifier.weight(1f))

      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // App Registration Button
        Text(
            text = tr(
                "Você será redirecionado para o app Meta AI para confirmar sua conexão.",
                "You'll be redirected to the Meta AI app to confirm your connection.",
            ),
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SwitchButton(
            label = tr("Conectar meus óculos", "Connect my glasses"),
            onClick = {
              activity?.let { viewModel.startRegistration(it) }
                  ?: Toast.makeText(context, activityNotAvailableMessage, Toast.LENGTH_SHORT).show()
            },
        )

        // Phone mode button
        SwitchButton(
            label = tr("Iniciar no Celular", "Start on Phone"),
            onClick = { viewModel.navigateToPhoneMode() },
        )
      }
    }
  }
}

@Composable
private fun TipItem(
    iconResId: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = tr("Ícone de dica", "Tip icon"),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = title,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
      )
      Text(text = text, color = Color.Gray)
    }
  }
}
