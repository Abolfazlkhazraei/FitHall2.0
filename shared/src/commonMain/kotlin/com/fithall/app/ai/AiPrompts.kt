package com.fithall.app.ai

import com.fithall.app.model.MacroTargets
import com.fithall.app.model.UserProfile

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

    const val MEAL_TEXT_SYSTEM = """You are a registered dietitian estimating the nutrition of a meal from the user's own description, which may come from speech-to-text and contain transcription mistakes.
Split the description into distinct foods and drinks. Use the amounts the user gives; when an amount is missing, assume a typical single portion and state the assumption in the portion field (for example "1 medium (approx. 120 g)").
Include cooking fats, sauces and drinks the user mentions, and estimate calories, protein, carbohydrates and fat using standard nutrition databases.
Use confidence "high" when amounts are specific, "medium" when you assumed typical portions, and "low" when the description is vague.
If the text doesn't describe food, return an empty items list, confidence "low", and say so in the notes.
Round all numbers to whole units."""

    fun mealTextUserText(description: String): String = "What I ate: $description"

    fun mealUserText(userNote: String): String =
        if (userNote.isBlank()) "Estimate the macros for this meal."
        else "Estimate the macros for this meal. Note from the user: $userNote"

    const val WORKOUT_SYSTEM = """You are an experienced certified strength and conditioning coach.
Design a training plan that fits the person's goal, experience, available equipment, training days and session length exactly.
Beginners get simple compound movements, full-body or upper/lower splits, conservative volume and clear form cues. Intermediate and advanced lifters get more specialised splits, more volume and progression methods suited to their level.
Choose exercises that are possible with the stated equipment only, and work around any listed injuries or limitations by picking safe alternatives.
Every session must fit the stated session length, including the warm-up.
Write the progression field as concrete week-by-week guidance (for example how to add weight or reps), and keep notes short and practical."""

    fun workoutUserText(profile: UserProfile, recentTraining: String): String = buildString {
        append("Create my training plan.\n\n")
        append(profileSummary(profile))
        if (recentTraining.isNotBlank()) {
            append("\n\nMy recent logged sessions (weight x reps of completed sets). ")
            append("Use them to pick realistic starting loads and to progress from where I am:\n")
            append(recentTraining)
        }
    }

    const val NUTRITION_SYSTEM = """You are a registered dietitian creating a practical one-day meal plan.
The plan must hit the provided daily calorie and macro targets as closely as possible (within about 5%), split across sensible meals and snacks.
Respect the person's dietary notes, allergies and preferences strictly. Prefer simple, affordable whole foods and give concrete portions in each meal description.
Tips should be short, actionable and specific to the person's goal.
Never plan fewer calories than the target, and don't suggest fasting, skipping meals, detoxes or other extreme measures."""

    fun nutritionUserText(profile: UserProfile, targets: MacroTargets): String = """Create my meal plan.

${profileSummary(profile)}

Daily targets: ${targets.calories} kcal, ${targets.proteinG} g protein, ${targets.carbsG} g carbs, ${targets.fatG} g fat."""

    const val COACH_SYSTEM = """You are FitHall Coach, a friendly and knowledgeable fitness and nutrition coach inside a mobile app.
You help the user reach their goal: answer questions about training and food, suggest meals that fit their remaining macros, adjust their plans when asked, and keep them motivated.
Base your advice on the user context below. Be concise: this is a phone chat, so prefer a few short paragraphs or a short list over long essays.
You give general fitness guidance, not medical advice: for pain, injuries, medical conditions, eating disorders or medication questions, recommend seeing a qualified professional.
Never advise eating below the user's daily calorie target, very low calorie diets, fasting to lose weight, purging, or exercising to "burn off" food, even if asked. If the user seems to be restricting, bingeing, or very anxious about food or weight, respond with care, don't give diet tips, and encourage them to talk to a doctor or an eating disorder helpline."""

    const val CHECK_IN_SYSTEM = """You are the user's fitness and nutrition coach doing their weekly check-in.
You receive a factual report computed by the app from what the user logged. Rely on its numbers; do not invent data that isn't there.

Judge progress against the user's goal:
- Lose fat: aim for a loss of about 0.5-1% of body weight per week. Faster than that risks muscle loss and fatigue, so suggest eating a little more; slower for 2+ weeks, suggest eating a little less.
- Build muscle: aim for a gain of about 0.25-0.5% per week, a bit less for advanced lifters. No gain for 2+ weeks, suggest eating a little more; much faster, suggest a little less.
- Get stronger: keep weight roughly stable or slowly rising; the priority is lifts going up.
- Endurance or maintain: keep weight within about 0.25% per week of stable.

Nutrition changes:
- Change calories in steps of 100-250 kcal, and only when the trend is clear. Use 0 when the data is too thin (fewer than 2 weigh-ins spread over at least 5 days, or most days unlogged), when the user is on track, or when the last check-in already changed calories and there hasn't been time to see its effect.
- Change protein only if intake is clearly short of target or the target is clearly wrong for the goal; otherwise use 0.
- Never take calories below the minimum allowed intake stated in the report.

Training changes:
- Only propose changes when there's a reason: a STALLED lift (for example change the rep range, sets, or swap to a close variation), consistently missed sessions, or very easy progress.
- Each change must name a day and an exercise exactly as written in the current plan. Use the same exercise name in newExercise if you only change sets, reps or rest.
- Propose at most 4 changes, and none if the plan is working or there is too little training data.

If the report contains a SAFETY RULE, follow it exactly.

Write for the user in second person: warm, specific and brief. Wins and concerns should reference their actual numbers. If data is missing, say what to log this week so the next check-in can be more precise. This is general fitness guidance, not medical advice."""

    fun checkInUserText(report: String): String = "Here is my report for this week's check-in.\n\n$report"

    /** Everything the AI needs about the user. Deliberately leaves out their name. */
    fun profileSummary(p: UserProfile): String = buildString {
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

    val CHECK_IN_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "headline": {"type": "string", "description": "One short sentence, e.g. 'Right on track: keep going'"},
        "summary": {"type": "string", "description": "2-4 sentences on how the week went"},
        "wins": {"type": "array", "items": {"type": "string"}},
        "concerns": {"type": "array", "items": {"type": "string"}},
        "calorieChange": {"type": "integer", "description": "Change to daily calories in kcal; 0 to keep"},
        "proteinChange": {"type": "integer", "description": "Change to daily protein in grams; 0 to keep"},
        "nutritionReason": {"type": "string", "description": "Why the nutrition changes (or why none)"},
        "trainingChanges": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "dayName": {"type": "string"},
              "currentExercise": {"type": "string"},
              "newExercise": {"type": "string"},
              "sets": {"type": "integer"},
              "reps": {"type": "string"},
              "restSeconds": {"type": "integer"},
              "reason": {"type": "string"}
            },
            "required": ["dayName", "currentExercise", "newExercise", "sets", "reps", "restSeconds", "reason"],
            "additionalProperties": false
          }
        },
        "focusForNextWeek": {"type": "array", "items": {"type": "string"}, "description": "1-3 concrete things to focus on"}
      },
      "required": ["headline", "summary", "wins", "concerns", "calorieChange", "proteinChange", "nutritionReason", "trainingChanges", "focusForNextWeek"],
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
