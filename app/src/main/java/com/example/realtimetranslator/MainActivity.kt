package com.example.realtimetranslator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = TranslationRepository()
        val viewModelFactory = TranslatorViewModelFactory(repository)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TranslatorApp(
                        viewModel = ViewModelProvider(this, viewModelFactory)[TranslatorViewModel::class.java]
                    )
                }
            }
        }
    }
}

enum class TranslateDirection(
    val sourceCode: String,
    val targetCode: String,
    val label: String,
    val sourceHint: String,
    val targetHint: String
) {
    ZhToEn(
        sourceCode = "zh-CN",
        targetCode = "en-US",
        label = "中文 -> English",
        sourceHint = "输入中文内容",
        targetHint = "English translation will appear here"
    ),
    EnToZh(
        sourceCode = "en-US",
        targetCode = "zh-CN",
        label = "English -> 中文",
        sourceHint = "Type English text",
        targetHint = "中文翻译会显示在这里"
    );

    fun toggle(): TranslateDirection = if (this == ZhToEn) EnToZh else ZhToEn
}

data class TranslatorUiState(
    val inputText: String = "",
    val translatedText: String = "",
    val direction: TranslateDirection = TranslateDirection.ZhToEn,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TranslatorViewModel(
    private val repository: TranslationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TranslatorUiState())
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .debounce(500)
                .distinctUntilChanged { old, new ->
                    old.inputText == new.inputText && old.direction == new.direction
                }
                .collectLatest { state ->
                    translateIfNeeded(state.inputText, state.direction)
                }
        }
    }

    fun updateInput(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                errorMessage = null
            )
        }
    }

    fun swapDirection() {
        _uiState.update {
            it.copy(
                inputText = it.translatedText,
                translatedText = it.inputText,
                direction = it.direction.toggle(),
                errorMessage = null
            )
        }
    }

    fun retry() {
        val state = _uiState.value
        viewModelScope.launch {
            translateIfNeeded(state.inputText, state.direction)
        }
    }

    private suspend fun translateIfNeeded(
        input: String,
        direction: TranslateDirection
    ) {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            _uiState.update {
                it.copy(
                    translatedText = "",
                    isLoading = false,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        runCatching {
            repository.translate(trimmedInput, direction)
        }.onSuccess { translated ->
            _uiState.update {
                it.copy(
                    translatedText = translated,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }.onFailure {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "翻译失败，请检查网络后重试。"
                )
            }
        }
    }
}

class TranslatorViewModelFactory(
    private val repository: TranslationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranslatorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TranslatorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class TranslationRepository {
    suspend fun translate(
        text: String,
        direction: TranslateDirection
    ): String = withContext(Dispatchers.IO) {
        val encodedText = Uri.encode(text)
        val url = URL(
            "https://api.mymemory.translated.net/get?q=$encodedText&langpair=${direction.sourceCode}|${direction.targetCode}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }

        try {
            val response = connection.inputStream.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
            val payload = JSONObject(response)
            val translated = payload
                .getJSONObject("responseData")
                .optString("translatedText")
                .trim()

            translated.ifBlank { "暂无翻译结果" }
        } finally {
            connection.disconnect()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorApp(viewModel: TranslatorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    TranslatorScreen(
        uiState = uiState,
        onInputChanged = viewModel::updateInput,
        onSwapDirection = viewModel::swapDirection,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    uiState: TranslatorUiState,
    onInputChanged: (String) -> Unit,
    onSwapDirection: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5F1E8),
                        Color(0xFFE5EEF8)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("中英实时翻译")
                        Text(
                            text = "Simple CN <-> EN translator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.direction.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onSwapDirection) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "Swap translation direction"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "输入时会自动翻译，适合做演示版 App。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("原文") },
                        placeholder = { Text(uiState.direction.sourceHint) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "译文",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            when {
                                uiState.isLoading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                        Text("翻译中...")
                                    }
                                }

                                uiState.errorMessage != null -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = uiState.errorMessage,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Button(onClick = onRetry) {
                                            Text("重试")
                                        }
                                    }
                                }

                                uiState.translatedText.isNotBlank() -> {
                                    Text(
                                        text = uiState.translatedText,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                else -> {
                                    Text(
                                        text = uiState.direction.targetHint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TranslatorScreenPreview() {
    MaterialTheme {
        TranslatorScreen(
            uiState = TranslatorUiState(
                inputText = "你好，今天怎么样？",
                translatedText = "Hello, how are you today?"
            ),
            onInputChanged = {},
            onSwapDirection = {},
            onRetry = {}
        )
    }
}
