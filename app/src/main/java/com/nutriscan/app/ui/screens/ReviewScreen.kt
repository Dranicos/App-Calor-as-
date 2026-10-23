package com.nutriscan.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutriscan.app.domain.Ingredient
import com.nutriscan.app.domain.MealType
import com.nutriscan.app.ui.AnalysisStatus
import com.nutriscan.app.ui.ReviewViewModel
import com.nutriscan.app.ui.components.MacroChips
import com.nutriscan.app.ui.components.NumberField
import com.nutriscan.app.ui.components.editable
import com.nutriscan.app.ui.components.g
import com.nutriscan.app.ui.components.kcal
import com.nutriscan.app.ui.components.toNumberOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(onDone: () -> Unit, vm: ReviewViewModel = viewModel()) {
    LaunchedEffect(vm.saved) { if (vm.saved) onDone() }

    // Índice del ingrediente que se está editando (-1 = nuevo, null = ninguno).
    var editing by remember { mutableStateOf<Int?>(null) }
    val analyzing = vm.status == AnalysisStatus.Analyzing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Editar comida" else "Revisar comida") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().padding(16.dp).imePadding()) {
                Button(
                    onClick = vm::save,
                    enabled = vm.ingredients.isNotEmpty() && !analyzing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (vm.isEditing) "Guardar cambios" else "Añadir al diario · ${vm.totals.kcal.kcal()}")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.image?.let { img ->
                Image(
                    img,
                    contentDescription = "Foto de la comida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                )
            }

            when (val s = vm.status) {
                AnalysisStatus.Analyzing -> AnalyzingCard()
                is AnalysisStatus.Error -> ErrorCard(
                    message = s.message,
                    canRetry = vm.hasPhoto,
                    onRetry = vm::analyze,
                    onDismiss = vm::dismissError,
                )
                AnalysisStatus.Idle -> Unit
            }

            if (!analyzing) {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    label = { Text("Nombre del plato") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { type ->
                        FilterChip(
                            selected = vm.mealType == type,
                            onClick = { vm.mealType = type },
                            label = { Text(type.label) },
                        )
                    }
                }

                TotalsCard(vm)

                if (vm.notes.isNotBlank()) {
                    Text(
                        buildString {
                            append(vm.notes)
                            if (vm.confidence.isNotBlank()) append(" (confianza ${vm.confidence})")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text("Ingredientes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Toca un ingrediente para corregir el nombre, los gramos o las calorías.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    if (vm.ingredients.isEmpty()) {
                        Text("Sin ingredientes todavía.", Modifier.padding(16.dp))
                    }
                    vm.ingredients.forEachIndexed { index, ing ->
                        if (index > 0) HorizontalDivider()
                        IngredientRow(ing, onClick = { editing = index }, onRemove = { vm.removeIngredient(index) })
                    }
                }
                OutlinedButton(onClick = { editing = -1 }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir ingrediente")
                }

                if (vm.ingredients.isNotEmpty()) {
                    Text("¿Comiste más o menos cantidad?", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("½" to 0.5, "−25 %" to 0.75, "+25 %" to 1.25, "×2" to 2.0).forEach { (label, f) ->
                            OutlinedButton(onClick = { vm.scaleAll(f) }) { Text(label) }
                        }
                    }
                }

                if (vm.hasPhoto) {
                    CorrectionCard(vm)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    editing?.let { index ->
        IngredientDialog(
            initial = vm.ingredients.getOrNull(index),
            onDismiss = { editing = null },
            onSave = { vm.updateIngredient(index, it); editing = null },
        )
    }
}

@Composable
private fun AnalyzingCard() {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Analizando con IA…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Identificando ingredientes y estimando calorías. Suele tardar unos segundos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, canRetry: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(message, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Añadir a mano") }
                if (canRetry) TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun TotalsCard(vm: ReviewViewModel) {
    val t = vm.totals
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total estimado", style = MaterialTheme.typography.labelLarge)
                Text(t.kcal.kcal(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Proteína ${t.protein.g()}", style = MaterialTheme.typography.bodyMedium)
                Text("Carbohidratos ${t.carbs.g()}", style = MaterialTheme.typography.bodyMedium)
                Text("Grasas ${t.fat.g()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IngredientRow(ing: Ingredient, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(ing.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(ing.kcal.kcal(), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "${ing.grams.roundToInt()} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MacroChips(ing.protein, ing.carbs, ing.fat)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Quitar") }
    }
}

@Composable
private fun CorrectionCard(vm: ReviewViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("¿La IA se ha equivocado?", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = vm.hint,
                onValueChange = { vm.hint = it },
                placeholder = { Text("Ej.: es tofu, no pollo · sin aceite · ración de 300 g") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = vm::analyze, enabled = vm.hint.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Volver a analizar con esta indicación")
            }
        }
    }
}

/** Diálogo para editar o añadir un ingrediente. Al cambiar los gramos recalcula el resto. */
@Composable
private fun IngredientDialog(initial: Ingredient?, onDismiss: () -> Unit, onSave: (Ingredient) -> Unit) {
    val base = initial ?: Ingredient("", 100.0, 0.0, 0.0, 0.0, 0.0)
    var name by remember { mutableStateOf(base.name) }
    var grams by remember { mutableStateOf(base.grams.editable()) }
    var kcal by remember { mutableStateOf(base.kcal.roundToInt().toString()) }
    var protein by remember { mutableStateOf(base.protein.editable()) }
    var carbs by remember { mutableStateOf(base.carbs.editable()) }
    var fat by remember { mutableStateOf(base.fat.editable()) }
    var autoScale by remember { mutableStateOf(initial != null) }

    fun onGramsChange(text: String) {
        grams = text
        val g = text.toNumberOrNull() ?: return
        if (autoScale && base.grams > 0) {
            val scaled = base.withGrams(g)
            kcal = scaled.kcal.roundToInt().toString()
            protein = scaled.protein.editable()
            carbs = scaled.carbs.editable()
            fat = scaled.fat.editable()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo ingrediente" else "Editar ingrediente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(grams, ::onGramsChange, "Cantidad", Modifier.fillMaxWidth(), suffix = "g")
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(autoScale, { autoScale = it })
                        Text("Recalcular calorías y macros al cambiar los gramos", style = MaterialTheme.typography.bodySmall)
                    }
                }
                NumberField(kcal, { kcal = it }, "Calorías", Modifier.fillMaxWidth(), suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it }, "Prot.", Modifier.weight(1f), suffix = "g")
                    NumberField(carbs, { carbs = it }, "Carb.", Modifier.weight(1f), suffix = "g")
                    NumberField(fat, { fat = it }, "Grasa", Modifier.weight(1f), suffix = "g")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && kcal.toNumberOrNull() != null,
                onClick = {
                    onSave(
                        Ingredient(
                            name = name.trim(),
                            grams = grams.toNumberOrNull() ?: 0.0,
                            kcal = kcal.toNumberOrNull() ?: 0.0,
                            protein = protein.toNumberOrNull() ?: 0.0,
                            carbs = carbs.toNumberOrNull() ?: 0.0,
                            fat = fat.toNumberOrNull() ?: 0.0,
                        )
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
