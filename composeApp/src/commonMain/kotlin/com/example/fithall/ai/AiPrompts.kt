package com.example.fithall.ai

import com.example.fithall.model.MacroTargets
import com.example.fithall.model.UserProfile

/**
 * Prompts and JSON schemas shared by every AiService implementation (and by a future backend).
 * Each schema mirrors a @Serializable class in model/Models.kt: keep the field names in sync.
 */
object AiPrompts {

    const val MEAL_SYSTEM = """You are a registered dietitian estimating the nutrition of a meal from a photo.
Identify each distinct food on the plate, estimate its portion from visual cues (plate size, utensils, hands, packaging), and estimate calories, protein, carbohydrates and fat for that portion using standard nutrition databases.
Account for hidden calories that are typical for the dish, such as cooking oil, butter and sauces, and say what you assumed in the notes.
If the user adds a note (for example "large portion" or "no oil"), treat it as more reliable than the photo.
If the photo does not show food, return an empty items list, confidence "low", and explain in the notes.
Round all numbers to whole units."""

    fun mealUserText(userNote: String): String =
        if (userNote.isBlank()) "Estimate the macros for this meal."
        else "Estimate the macros for this meal. Note from the user: $userNote"

    const val WORKOUT_SYSTEM = """You are an experienced certified strength and conditioning coach.
Design a training plan that fits the person's goal, experience, available equipment, training days and session length exactly.
Beginners get simple compound movements, full-body or upper/lower splits, conservative volume and clear form cues. Intermediate and advanced lifters get more specialised splits, more volume and progression methods suited to their level.
Choose exercises that are possible with the stated equipment only, and work around any listed injuries or limitations by picking safe alternatives.
Every session must fit the stated session length, including the warm-up.
Write the progression field as concrete week-by-week guidance (for example how to add weight or reps), and keep notes short and practical."""

    fun workoutUserText(profile: UserProfile): String = """Create my training plan.

${profileSummary(profile)}"""

    const val NUTRITION_SYSTEM = """You are a registered dietitian creating a practical one-day meal plan.
The plan must hit the provided daily calorie and macro targets as closely as possible (within about 5%), split across sensible meals and snacks.
Respect the person's dietary notes, allergies and preferences strictly. Prefer simple, affordable whole foods and give concrete portions in each meal description.
Tips should be short, actionable and specific to the person's goal."""

    fun nutritionUserText(profile: UserProfile, targets: MacroTargets): String = """Create my meal plan.

${profileSummary(profile)}

Daily targets: ${targets.calories} kcal, ${targets.proteinG} g protein, ${targets.carbsG} g carbs, ${targets.fatG} g fat."""

    const val COACH_SYSTEM = """You are FitHall Coach, a friendly and knowledgeable fitness and nutrition coach inside a mobile app.
You help the user reach their goal: answer questions about training and food, suggest meals that fit their remaining macros, adjust their plans when asked, and keep them motivated.
Base your advice on the user context below. Be concise: this is a phone chat, so prefer a few short paragraphs or a short list over long essays.
You give general fitness guidance, not medical advice: for pain, injuries, medical conditions, eating disorders or medication questions, recommend seeing a qualified professional."""

    fun profileSummary(p: UserProfile): String = buildString {
        appendLine("Name: ${p.name}")
        appendLine("Age: ${p.age}, sex: ${p.sex.label}")
        appendLine("Height: ${p.heightCm} cm, weight: ${p.weightKg} kg")
        appendLine("Activity level: ${p.activityLevel.label} (${p.activityLevel.description})")
        appendLine("Training experience: ${p.experience.label} (${p.experience.description})")
        appendLine("Goal: ${p.goal.label}")
        appendLine("Equipment: ${p.equipment.label}")
        appendLine("Training days per week: ${p.trainingDaysPerWeek}, session length: ${p.sessionMinutes} minutes")
        if (p.dietaryNotes.isNotBlank()) appendLine("Dietary notes: ${p.dietaryNotes}")
        if (p.injuries.isNotBlank()) appendLine("Injuries / limitations: ${p.injuries}")
    }.trim()

    val MEAL_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "mealName": {"type": "string", "description": "Short name for the meal, e.g. 'Chicken rice bowl'"},
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "name": {"type": "string"},
              "portion": {"type": "string", "description": "Estimated portion, e.g. '150 g' or '1 cup'"},
              "calories": {"type": "integer"},
              "proteinG": {"type": "integer"},
              "carbsG": {"type": "integer"},
              "fatG": {"type": "integer"}
            },
            "required": ["name", "portion", "calories", "proteinG", "carbsG", "fatG"],
            "additionalProperties": false
          }
        },
        "confidence": {"type": "string", "enum": ["high", "medium", "low"]},
        "notes": {"type": "string", "description": "Assumptions made, such as cooking oil or hidden ingredients"}
      },
      "required": ["mealName", "items", "confidence", "notes"],
      "additionalProperties": false
    }
    """.trimIndent()

    val WORKOUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "title": {"type": "string"},
        "summary": {"type": "string", "description": "2-3 sentences on why this plan fits the person"},
        "durationWeeks": {"type": "integer"},
        "days": {
          "type": "array",
          "description": "One entry per training day in the week",
          "items": {
            "type": "object",
            "properties": {
              "dayName": {"type": "string", "description": "e.g. 'Day 1' or 'Monday'"},
              "focus": {"type": "string", "description": "e.g. 'Upper body push'"},
              "durationMinutes": {"type": "integer"},
              "warmup": {"type": "string"},
              "exercises": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "sets": {"type": "integer"},
                    "reps": {"type": "string", "description": "e.g. '8-10', '12 each side' or '30 s'"},
                    "restSeconds": {"type": "integer"},
                    "notes": {"type": "string", "description": "Short form cue or intensity guidance"}
                  },
                  "required": ["name", "sets", "reps", "restSeconds", "notes"],
                  "additionalProperties": false
                }
              }
            },
            "required": ["dayName", "focus", "durationMinutes", "warmup", "exercises"],
            "additionalProperties": false
          }
        },
        "progression": {"type": "string"},
        "safetyNotes": {"type": "string"}
      },
      "required": ["title", "summary", "durationWeeks", "days", "progression", "safetyNotes"],
      "additionalProperties": false
    }
    """.trimIndent()

    val NUTRITION_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "summary": {"type": "string"},
        "meals": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "name": {"type": "string", "description": "e.g. 'Breakfast'"},
              "description": {"type": "string", "description": "Foods with portions"},
              "calories": {"type": "integer"},
              "proteinG": {"type": "integer"},
              "carbsG": {"type": "integer"},
              "fatG": {"type": "integer"}
            },
            "required": ["name", "description", "calories", "proteinG", "carbsG", "fatG"],
            "additionalProperties": false
          }
        },
        "tips": {"type": "array", "items": {"type": "string"}}
      },
      "required": ["summary", "meals", "tips"],
      "additionalProperties": false
    }
    """.trimIndent()
}
