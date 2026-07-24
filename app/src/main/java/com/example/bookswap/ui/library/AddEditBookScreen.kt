package com.example.bookswap.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bookswap.R
import com.example.bookswap.data.model.BookCondition
import com.example.bookswap.data.model.BookOptions

private const val MAX_IMAGES = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBookScreen(
    bookId: String?,
    onDone: () -> Unit,
    viewModel: AddEditBookViewModel = viewModel(),
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = uiState.existing

    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var author by rememberSaveable(existing?.id) { mutableStateOf(existing?.author.orEmpty()) }
    var isbn by rememberSaveable(existing?.id) { mutableStateOf(existing?.isbn.orEmpty()) }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var language by rememberSaveable(existing?.id) { mutableStateOf(existing?.language.orEmpty()) }
    var condition by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.condition ?: BookCondition.GOOD.name)
    }
    var conditionConfirmed by rememberSaveable(existing?.id) { mutableStateOf(false) }

    // Kept existing image URLs (editable/removable) + newly picked local URIs.
    val keptUrls = remember(existing?.id) { mutableStateListOf<String>().apply { addAll(existing?.imageUrls.orEmpty()) } }
    val newUris = remember(existing?.id) { mutableStateListOf<Uri>() }
    val totalImages = keptUrls.size + newUris.size

    // Apply an ISBN lookup result once it arrives.
    LaunchedEffect(uiState.lookup) {
        uiState.lookup?.let { (t, a) ->
            title = t
            if (a.isNotBlank()) author = a
            viewModel.consumeLookup()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris ->
        val remaining = MAX_IMAGES - totalImages
        newUris.addAll(uris.take(remaining.coerceAtLeast(0)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (bookId == null) R.string.book_add_title else R.string.book_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoadingBook) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ISBN + lookup
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = isbn,
                    onValueChange = { isbn = it },
                    label = { Text(stringResource(R.string.book_isbn)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { viewModel.lookupIsbn(isbn) },
                    enabled = !uiState.isLookingUp && isbn.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    if (uiState.isLookingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.book_lookup))
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.book_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.book_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = stringResource(R.string.book_category),
                options = BookOptions.categories,
                selected = category,
                onSelected = { category = it },
            )
            DropdownField(
                label = stringResource(R.string.book_language),
                options = BookOptions.languages,
                selected = language,
                onSelected = { language = it },
            )
            DropdownField(
                label = stringResource(R.string.book_condition),
                options = BookCondition.entries.map { it.name },
                selected = condition,
                display = { BookCondition.labelOf(it) },
                onSelected = { condition = it },
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.book_description)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            // Images (1–5)
            Text(
                stringResource(R.string.book_photos_count, totalImages, MAX_IMAGES),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(keptUrls) { index, url ->
                    Thumbnail(model = url, onRemove = { keptUrls.removeAt(index) })
                }
                items(newUris) { uri ->
                    Thumbnail(model = uri, onRemove = { newUris.remove(uri) })
                }
                if (totalImages < MAX_IMAGES) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.book_add_photo))
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = conditionConfirmed, onCheckedChange = { conditionConfirmed = it })
                Text(stringResource(R.string.book_condition_confirm), style = MaterialTheme.typography.bodyMedium)
            }

            uiState.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    viewModel.save(
                        title = title,
                        author = author,
                        isbn = isbn,
                        category = category,
                        language = language,
                        description = description,
                        condition = condition,
                        keptImageUrls = keptUrls.toList(),
                        newImageUris = newUris.toList(),
                        conditionConfirmed = conditionConfirmed,
                        onSaved = onDone,
                    )
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.book_save))
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(model: Any, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(88.dp)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    display: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (selected.isBlank()) "" else display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
