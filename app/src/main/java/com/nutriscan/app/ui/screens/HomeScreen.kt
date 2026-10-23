package com.nutriscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriscan.app.data.MealEntity
import com.nutriscan.app.domain.Macros
import com.nutriscan.app.domain.plan
import com.nutriscan.app.ui.MainViewModel
import com.nutriscan.app.ui.components.CalorieRing
import com.nutriscan.app.ui.components.MacroBar
import com.nutriscan.app.ui.components.MealCard
import com.nutriscan.app.ui.theme.MacroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val spanish: Locale = Locale.forLanguageTag("es-ES")

fun List<MealEntity>.sum(): Macros = fold(Macros()) { acc, m -> acc + Macros(m.kcal, m.protein, m.carbs, m.fat) }

@Composable
fun HomeScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpenMeal: (Long) -> Unit,
    onGoToPlan: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val meals by vm.todayMeals.collectAsStateWithLifecycle()
    val plan = settings?.profile?.plan()
    val total = meals.sum()
    var toDelete by remember { mutableStateOf<MealEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Hoy", style = MaterialTheme.typography.headlineMedium)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish))
                    .replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val s = settings
        if (s != null && !s.hasActiveKey) {
            item {
                HintCard(
                    icon = Icons.Outlined.Key,
                    text = "Para escanear fotos necesitas una API key de ${s.provider.company} (${s.provider.label}).",
                    action = "Configurar",
                    onClick = onGoToSettings,
                )
            }
        }
        if (s != null && s.profile == null) {
            item {
                HintCard(
                    icon = Icons.Outlined.Person,
                    text = "Introduce tu peso y altura para calcular cuántas calorías necesitas.",
                    action = "Crear plan",
                    onClick = onGoToPlan,
                )
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CalorieRing(consumed = total.kcal, target = plan?.targetKcal ?: 0)
                    Spacer(Modifier.height(16.dp))
                    MacroBar("Proteína", total.protein, plan?.proteinG, MacroColors.protein)
                    Spacer(Modifier.height(10.dp))
                    MacroBar("Carbohidratos", total.carbs, plan?.carbsG, MacroColors.carbs)
                    Spacer(Modifier.height(10.dp))
                    MacroBar("Grasas", total.fat, plan?.fatG, MacroColors.fat)
                }
            }
        }

        item {
            Text("Comidas de hoy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
        if (meals.isEmpty()) {
            item {
                Text(
                    "Aún no has registrado nada. Pulsa «Escanear» para hacer una foto a tu plato o producto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(meals, key = { it.id }) { meal ->
            MealCard(meal, vm.photos, onClick = { onOpenMeal(meal.id) }, onDelete = { toDelete = meal })
        }
    }

    toDelete?.let { meal ->
        ConfirmDialog(
            title = "¿Borrar «${meal.name}»?",
            text = "Se eliminará del diario.",
            confirm = "Borrar",
            onConfirm = { vm.deleteMeal(meal); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
fun HintCard(icon: ImageVector, text: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
