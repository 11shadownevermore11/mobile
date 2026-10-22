package ru.kodnovorossii.app.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Алгоритмы, работающие на устройстве без сети: поиск, «открыто сейчас», расстояния, оптимизация маршрута. */
object Logic {
    private val TZ: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val DAYS = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat") // Calendar.DAY_OF_WEEK - 1

    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }

    fun fmtDist(m: Double?): String = when {
        m == null -> ""
        m < 1000 -> "${m.roundToInt()} м"
        else -> String.format("%.1f км", m / 1000)
    }

    fun fmtMin(m: Int): String = if (m >= 60) "${m / 60} ч" + (if (m % 60 > 0) " ${m % 60} мин" else "") else "$m мин"

    private fun now(): Calendar = Calendar.getInstance(TZ)

    fun todayKey(): String = DAYS[now().get(Calendar.DAY_OF_WEEK) - 1]

    fun todayHours(p: PlaceDto): String {
        val h = p.hours ?: return "—"
        if (h["always"] == true) return "Круглосуточно"
        return (h[todayKey()] as? String) ?: "Выходной"
    }

    /** «Открыто сейчас» считается на устройстве — актуально и офлайн. */
    fun isOpenNow(p: PlaceDto): Boolean {
        if (p.status != "open") return false
        val h = p.hours ?: return false
        if (h["always"] == true) return true
        val c = now()
        val rng = h[DAYS[c.get(Calendar.DAY_OF_WEEK) - 1]] as? String ?: return false
        val parts = rng.split("-")
        if (parts.size != 2) return false
        fun mins(s: String): Int? = s.trim().split(":").takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.times(60)?.plus(b.toIntOrNull() ?: 0) }
        val start = mins(parts[0]) ?: return false
        val end = mins(parts[1]) ?: return false
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (end <= start) m >= start || m < end else m in start until end
    }

    // ---------- Офлайн-поиск: морфология (упрощённый стемминг), опечатки, неверная раскладка, синонимы ----------
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"
    private val ENDINGS = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими", "ая", "яя", "ое", "ее", "ие", "ые", "ой", "ей", "ий", "ый",
        "ам", "ям", "ах", "ях", "ов", "ев", "ом", "ем", "ую", "юю", "ия", "ья", "ть", "а", "я", "о", "е", "ы", "и", "у", "ю", "ь",
    ).sortedByDescending { it.length }
    private val SYN = mapOf(
        "поесть" to listOf("кафе", "ресторан", "кофейня", "пекарня"), "кофе" to listOf("кофейня", "кафе"),
        "переночевать" to listOf("отель", "гостиница", "хостел"), "погулять" to listOf("парк", "набережная", "бульвар"),
        "море" to listOf("морской", "маяк", "пляж", "приазовье"), "айти" to listOf("it", "игры"), "завод" to listOf("фабрика", "предприятие", "производство"),
        "дети" to listOf("детьми", "семейный"), "театр" to listOf("опера", "драма"), "искусство" to listOf("музей", "галерея", "арт"),
    )

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
    fun tokens(s: String): List<String> = normalize(s).split(Regex("[\\s\\-_/]+")).filter { it.length > 1 }

    fun stem(w: String): String {
        if (w.length <= 4) return w
        for (e in ENDINGS) if (w.endsWith(e) && w.length - e.length >= 3) return w.dropLast(e.length)
        return w
    }

    fun fixLayout(w: String): String? {
        if (!w.all { it in EN }) return null
        return w.map { ch -> val i = EN.indexOf(ch); if (i >= 0) RU[i] else ch }.joinToString("")
    }

    private fun lev(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 99
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = min(min(d[j] + 1, d[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    data class Hit(val place: PlaceDto, val score: Double)

    fun search(query: String, places: List<PlaceDto>): Pair<List<Hit>, String?> {
        val q = tokens(query)
        if (q.isEmpty()) return places.map { Hit(it, 0.0) } to null
        var hint: String? = null
        val expanded = q.map { t ->
            val variants = mutableMapOf<String, Double>()
            variants[stem(t)] = 1.0
            fixLayout(t)?.let { variants[stem(it)] = 0.95; hint = it }
            SYN[t]?.forEach { variants[stem(it)] = 0.8 }
            variants
        }
        val hits = places.mapNotNull { p ->
            val fields = listOf(
                6.0 to p.name, 3.5 to (p.category?.name ?: ""), 3.0 to (p.tags ?: emptyList()).joinToString(" "),
                2.0 to "${p.address} ${p.city}", 1.0 to "${p.short} ${p.description}",
            ).map { (w, text) -> w to tokens(text).map { stem(it) } }
            var total = 0.0
            var matched = 0
            for (exp in expanded) {
                var best = 0.0
                for ((w, words) in fields) for ((st, ew) in exp) for (word in words) {
                    val s = when {
                        word == st -> w * ew
                        st.length >= 4 && word.startsWith(st) -> w * ew * 0.6
                        st.length >= 4 && lev(word, st) <= (if (st.length >= 7) 2 else 1) -> w * ew * 0.55
                        else -> 0.0
                    }
                    if (s > best) best = s
                }
                if (best > 0) { matched++; total += best }
            }
            if (matched == 0 || (expanded.size >= 2 && matched < expanded.size - (if (expanded.size >= 3) 1 else 0))) null
            else Hit(p, total * (matched.toDouble() / expanded.size).let { it * it } + if (normalize(p.name).contains(normalize(query).trim())) 8.0 else 0.0)
        }
        val top = hits.maxOfOrNull { it.score } ?: 0.0
        return hits.filter { it.score >= top * 0.3 }.sortedByDescending { it.score } to hint
    }

    // ---------- Маршрут: ближайший сосед + 2-opt, расписание с паузами ----------
    fun optimize(points: List<PlaceDto>, start: Pair<Double, Double>? = null): List<PlaceDto> {
        if (points.size <= 2) return points
        fun d(a: PlaceDto, b: PlaceDto) = haversine(a.lat, a.lng, b.lat, b.lng)
        val first = if (start != null) points.minBy { haversine(start.first, start.second, it.lat, it.lng) } else points.minBy { it.lng }
        val order = mutableListOf(first)
        val left = points.toMutableList().apply { remove(first) }
        while (left.isNotEmpty()) {
            val nxt = left.minBy { d(order.last(), it) }
            order.add(nxt); left.remove(nxt)
        }
        fun len(o: List<PlaceDto>) = o.zipWithNext().sumOf { (a, b) -> d(a, b) }
        var best = order.toList()
        var improved = true
        while (improved) {
            improved = false
            for (i in 1 until best.size - 1) for (k in i + 1 until best.size) {
                val cand = best.subList(0, i) + best.subList(i, k + 1).reversed() + best.subList(k + 1, best.size)
                if (len(cand) + 1e-6 < len(best)) { best = cand; improved = true }
            }
        }
        return best
    }

    fun travelMin(m: Double, car: Boolean) = maxOf(1, (m * 1.3 / 1000 / (if (car) 30.0 else 4.5) * 60).roundToInt())

    fun stayFor(p: PlaceDto, categories: Map<Int, CategoryDto>): Int = categories[p.category?.id ?: -1]?.default_stay_min?.takeIf { it > 0 } ?: 45

    /** Строит расписание локально — личный маршрут из избранного работает офлайн. */
    fun timeline(places: List<PlaceDto>, categories: Map<Int, CategoryDto>, startTime: String = "10:00", car: Boolean = false): RouteDto {
        val (hh, mm) = startTime.split(":").map { it.toIntOrNull() ?: 0 }
        var t = hh * 60 + mm
        var dist = 0.0
        val stops = mutableListOf<StopDto>()
        places.forEachIndexed { i, p ->
            var tm = 0
            var dm = 0.0
            if (i > 0) { dm = haversine(places[i - 1].lat, places[i - 1].lng, p.lat, p.lng); tm = travelMin(dm, car); dist += dm; t += tm }
            val food = p.category?.slug in setOf("cafe", "coffee", "restaurant")
            val stay = if (food) 40 else stayFor(p, categories)
            fun f(x: Int) = String.format("%02d:%02d", (x / 60) % 24, x % 60)
            stops.add(StopDto(order = i + 1, kind = if (food) "pause" else "visit", pause_type = if (food) "cafe" else null, stay_min = stay,
                arrive = f(t), leave = f(t + stay), travel_min = tm, travel_m = dm.roundToInt(), place = p))
            t += stay
        }
        return RouteDto(title = "Мой маршрут", stops = stops, duration_min = t - (hh * 60 + mm), distance_km = (dist / 10).roundToInt() / 100.0,
            start = startTime, finish = String.format("%02d:%02d", (t / 60) % 24, t % 60), polyline = places.map { listOf(it.lat, it.lng) },
            transport = if (car) "car" else "walk")
    }
}
