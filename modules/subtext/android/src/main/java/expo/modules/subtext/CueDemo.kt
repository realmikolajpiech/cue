package expo.modules.subtext

import org.json.JSONObject

/** Fictional conversation; real model calls use only this room's isolated memory. */
internal object CueDemo {
  const val ID = "messenger:subtext-demo"
  fun messages(stage: Int, base: Long): List<JSONObject> {
    require(stage in 0..3)
    val exchanges = listOf(
      false to "Dzięki za bilet! Wiszę Ci 50 zł. Oddam jutro.",
      true to "spoko, dzięki za wspólny wieczór :)",
      false to "Było super, musimy to powtórzyć!",
      true to "no pewnie, daj znać jak będziesz miała czas",
      false to "Jednak przeleję pojutrze, pasuje?",
      true to "jasne, bez pośpiechu",
      false to "Przelałam teraz 20 zł z tych 50 zł. Resztę oddam pojutrze.",
      true to "mam te 20, dzięki!",
      false to "Wysłałam też pozostałe 30 zł.",
      true to "dotarło, jesteśmy rozliczeni :)"
    ).take(listOf(4, 6, 8, 10)[stage])
    return exchanges.mapIndexed { index, (me, text) -> JSONObject()
      .put("id", "cue-demo-${index + 1}").put("sender", if (me) "Ty" else "Marta")
      .put("isMe", me).put("text", text).put("timestamp", base - 600_000 + index * 30_000) }
  }
}
