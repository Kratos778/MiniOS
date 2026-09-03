package com.minios.elizierdias.apps.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TerminalApp() {
    val lines = remember {
        mutableStateListOf("MiniOS Terminal v0.1 — demo", "comandos: help, echo, whoami, uname, clear", "")
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun run(cmd: String) {
        lines.add("guest@minios:~$ $cmd")
        val out = when {
            cmd.isBlank() -> ""
            cmd == "help" -> "help | echo <t> | whoami | uname | clear"
            cmd == "whoami" -> "guest"
            cmd == "uname" -> "MiniOS Shell 0.1.0"
            cmd == "clear" -> { lines.clear(); "" }
            cmd.startsWith("echo ") -> cmd.removePrefix("echo ")
            else -> "comando nao reconhecido: $cmd"
        }
        if (out.isNotEmpty()) lines.add(out)
        lines.add("")
        scope.launch { listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0)) }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF010409)).padding(10.dp)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(lines) { line ->
                Text(line, color = Color(0xFF3FB950), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
        TextField(
            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFC9D1D9)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0D1117), unfocusedContainerColor = Color(0xFF0D1117),
                focusedIndicatorColor = Color(0xFF238636), unfocusedIndicatorColor = Color(0xFF30363D),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { run(input.trim()); input = "" }),
        )
    }
}
