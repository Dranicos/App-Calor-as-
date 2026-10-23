package com.nutriscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriscan.app.domain.Activity
import com.nutriscan.app.domain.Goal
import com.nutriscan.app.domain.Plan
import com.nutriscan.app.domain.Profile
import com.nutriscan.app.domain.Sex
import com.nutriscan.app.domain.plan
import com.nutriscan.app.ui.MainViewModel
import com.nutriscan.app.ui.components.NumberField
import com.nutriscan.app.ui.components.editable
import com.nutriscan.app.ui.components.toNumberOrNull
import com.nutriscan.app.ui.theme.MacroColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved = settings?.profile

    var sex by rememberSaveable { mutableStateOf(Sex.HOMBRE) }
    var age by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableStateOf(Activity.LIGERO) }
    var goal by rememberSaveable { mutableStateOf(Goal.MANTENER) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    // Rellena el formulario con el perfil guardado la primera vez.
    LaunchedEffect(saved) {
        if (saved != null && !loaded) {
            sex = saved.sex; age = saved.age.toString(); weight = saved.weightKg.editable()
            height = saved.heightCm.editable(); activity = saved.activity; goal = saved.goal
            loaded = true
        }
    }

    val draft = run {
        val a = age.toNumberOrNull()?.toInt()
        val w = weight.toNumberOrNull()
        val h = height.toNumberOrNull()
        if (a != null && a in 14..100 && w != null && w in 30.0..300.0 && h != null && h in 120.0..230.0) {
            Profile(sex, a, w, h, activity, goal)
        } else null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mi plan", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Calcula cuántas calorías y macronutrientes deberías comer al día según tus datos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach {
                FilterChip(selected = sex == it, onClick = { sex = it; justSaved = false }, label = { Text(it.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(age, { age = it; justSaved = false }, "Edad", Modifier.weight(1f), suffix = "años")
            NumberField(weight, { weight = it; justSaved = false }, "Peso", Modifier.weight(1f), suffix = "kg")
            NumberField(height, { height = it; justSaved = false }, "Altura", Modifier.weight(1f), suffix = "cm")
        }

        Text("Actividad física", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Activity.entries.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = activity == a, onClick = { activity = a; justSaved = false })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = activity == a, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(a.label, style = MaterialTheme.typography.bodyLarge)
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text("Objetivo", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach {
                FilterChip(selected = goal == it, onClick = { goal = it; justSaved = false }, label = { Text(it.label) })
            }
        }

        if (draft == null) {
            Text(
                "Introduce edad (14–100), peso (30–300 kg) y altura (120–230 cm) para ver tu plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PlanCard(draft.plan())
            Button(
                onClick = { vm.saveProfile(draft); justSaved = true },
                enabled = draft != saved,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (justSaved || draft == saved) "Plan guardado ✓" else "Guardar mi plan") }
        }

        Text(
            "Cálculo con la fórmula de Mifflin-St Jeor. Es una estimación orientativa y no sustituye el consejo de un profesional sanitario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanCard(plan: Plan) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Tu objetivo diario", style = MaterialTheme.typography.labelLarge)
            Text("${plan.targetKcal} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroTarget("Proteína", plan.proteinG, MacroColors.protein)
                MacroTarget("Carbohidratos", plan.carbsG, MacroColors.carbs)
                MacroTarget("Grasas", plan.fatG, MacroColors.fat)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            InfoLine("Metabolismo basal", "${plan.bmr} kcal")
            InfoLine("Gasto diario total", "${plan.tdee} kcal")
            InfoLine("IMC", String.format(Locale.US, "%.1f · %s", plan.bmi, plan.bmiLabel))
            InfoLine("Agua recomendada", String.format(Locale.US, "%.1f L", plan.waterLiters))
        }
    }
}

@Composable
private fun MacroTarget(label: String, grams: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$grams g", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
