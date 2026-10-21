package ru.kodnovorossii.app.data

// Модели общего API (тот же бэкенд, что и у сайта). Поля nullable там, где сервер может их не прислать.

data class FieldDto(
    val key: String = "",
    val label: String = "",
    val type: String? = "text",
    val options: List<String>? = null,
    val unit: String? = null,
    val filterable: Boolean? = false,
    val required: Boolean? = false,
    val value: Any? = null,
)

data class CategoryDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val icon: String? = null,
    val fields: List<FieldDto>? = null,
    val default_stay_min: Int = 45,
)

data class VerticalDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val short: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val stage: Int = 1,
    val is_professional: Boolean = false,
    val enabled: Boolean = true,
    val map_layer: Boolean = true,
    val count: Int = 0,
    val form_title: String? = null,
    val form_fields: List<FieldDto>? = null,
    val categories: List<CategoryDto>? = null,
)

data class ConfigDto(
    val version: String? = null,
    val verticals: List<VerticalDto>? = null,
    val dictionaries: Map<String, Any?>? = null,
)

data class RefDto(
    val id: Int? = null,
    val slug: String? = null,
    val name: String? = null,
    val color: String? = null,
    val icon: String? = null,
)

data class PlaceDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val short: String? = null,
    val address: String? = null,
    val city: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val photos: List<String>? = null,
    val photo: String? = null, // в событиях сервер присылает одно фото места
    val status: String? = "open",
    val rating: Double = 0.0,
    val reviews_count: Int = 0,
    val price_level: Int = 1,
    val tags: List<String>? = null,
    val labels: List<String>? = null,
    val formats: List<String>? = null,
    val made_in_region: Boolean = false,
    val accessible: Boolean = false,
    val open_now: Boolean? = null,
    val today_hours: String? = null,
    val distance_m: Int? = null,
    val vertical: RefDto? = null,
    val category: RefDto? = null,
    val views: Int = 0,
    val favorites_count: Int = 0,
    val description: String? = null,
    val hours: Map<String, Any?>? = null,
    val phone: String? = null,
    val website: String? = null,
    val email: String? = null,
    val last_checked: String? = null,
    val avg_check: Int? = null,
    val services: List<Map<String, Any?>>? = null,
    val portfolio: List<Map<String, Any?>>? = null,
    val attrs: Map<String, Any?>? = null,
    val fields: List<FieldDto>? = null,
)

data class EventDto(
    val id: Int = 0,
    val slug: String = "",
    val title: String = "",
    val kind: String = "",
    val start: String = "",
    val end: String? = null,
    val description: String? = null,
    val price: Int = 0,
    val age: String? = null,
    val registration: Boolean = false,
    val capacity: Int = 0,
    val booked: Int = 0,
    val place: PlaceDto? = null,
    val vertical: RefDto? = null,
    val photo: String? = null, // своё фото события (photos/events/<slug>.jpg на сервере)
)

data class StopDto(
    val order: Int = 0,
    val kind: String = "visit",
    val pause_type: String? = null,
    val note: String? = null,
    val stay_min: Int = 0,
    val arrive: String = "",
    val leave: String = "",
    val travel_min: Int = 0,
    val travel_m: Int = 0,
    val place: PlaceDto = PlaceDto(),
)

data class RouteDto(
    val id: Int = 0,
    val slug: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val color: String? = null,
    val transport: String? = "walk",
    val duration_min: Int = 0,
    val distance_km: Double = 0.0,
    val start: String? = null,
    val finish: String? = null,
    val stops: List<StopDto>? = null,
    val polyline: List<List<Double>>? = null,
    val verticals: List<RefDto>? = null,
    val saved_km: Double? = null,
)

data class PromoDto(
    val id: Int = 0,
    val place_id: Int = 0,
    val code: String = "",
    val discount: Int = 5,
    val description: String? = null,
    val valid_until: String? = null,
    val activations: Int = 0,
    val place: PlaceDto? = null,
)

data class SiteDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val kind: String = "",
    val city: String? = null,
    val address: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val polygon: List<List<Double>>? = null,
    val area_ha: Double = 0.0,
    val building_m2: Int = 0,
    val power_mw: Double = 0.0,
    val gas: Boolean = false,
    val water: Boolean = false,
    val sewage: Boolean = false,
    val railway: Boolean = false,
    val highway_km: Double = 0.0,
    val status: String = "free",
    val ownership: String? = null,
    val deal: String? = null,
    val price: String? = null,
    val industries: List<String>? = null,
    val benefits: List<String>? = null,
    val description: String? = null,
    val before: Map<String, Any?>? = null,
    val after: Map<String, Any?>? = null,
    val residents: List<String>? = null,
)

data class OpportunityDto(
    val id: Int = 0,
    val kind: String = "",
    val title: String = "",
    val org: String? = null,
    val industry: String? = null,
    val description: String? = null,
    val format: String? = null,
    val salary: String? = null,
    val deadline: String? = null,
    val contact: String? = null,
)

data class StoryDto(
    val id: Int = 0,
    val place_id: Int? = null,
    val title: String = "",
    val text: String = "",
    val period: String? = null,
    val author: String? = null,
    val author_kind: String? = null,
    val listens: Int = 0,
    val audio_url: String? = null,
)

data class InfoItem(val title: String = "", val text: String = "")
data class InfoSection(val id: String = "", val icon: String? = null, val title: String = "", val items: List<InfoItem>? = null)

data class BundleDto(
    val version: String? = null,
    val generated_at: String? = null,
    val config: ConfigDto? = null,
    val places: List<PlaceDto>? = null,
    val events: List<EventDto>? = null,
    val routes: List<RouteDto>? = null,
    val promos: List<PromoDto>? = null,
    val sites: List<SiteDto>? = null,
    val opportunities: List<OpportunityDto>? = null,
    val heritage: List<StoryDto>? = null,
    val info: List<InfoSection>? = null,
)

data class UserDto(
    val id: Int = 0,
    val email: String = "",
    val name: String = "",
    val role: String = "user",
    val role_title: String? = null,
    val is_service: Boolean = false,
    val owner_verified: Boolean = false,
    val is_expert: Boolean = false,
    val totp_enabled: Boolean = false,
    val interests: List<String>? = null,
)

data class LoginResponse(
    val token: String? = null,
    val user: UserDto? = null,
    val need_totp: Boolean? = null,
    val need_totp_setup: Boolean? = null,
    val setup_token: String? = null,
)

data class ReviewDto(
    val id: Int = 0,
    val user_id: Int = 0,
    val user_name: String? = null,
    val rating: Int = 0,
    val text: String? = null,
    val owner_reply: String? = null,
    val created_at: String? = null,
)

data class ChatRoute(val title: String? = null, val duration_min: Int = 0, val distance_km: Double = 0.0, val finish: String? = null,
                     val stops: List<Map<String, Any?>>? = null, val stops_raw: List<Map<String, Any?>>? = null)

data class ChatReply(
    val reply: String = "",
    val places: List<PlaceDto>? = null,
    val events: List<EventDto>? = null,
    val route: ChatRoute? = null,
    val suggestions: List<String>? = null,
)

data class PromoActivation(val code: String = "", val discount: Int = 0, val qr: String = "")

data class Achievement(val id: String = "", val title: String = "", val description: String = "", val done: Boolean = false)
data class Stamp(val at: String = "", val place: PlaceDto = PlaceDto())
data class PassportDto(val stamps: List<Stamp>? = null, val achievements: List<Achievement>? = null, val level: Int = 1, val next_level_in: Int = 3)

data class MatchResult(val site: SiteDto = SiteDto(), val score: Int = 0, val reasons: List<String>? = null, val misses: List<String>? = null)

data class MessageResponse(val ok: Boolean? = null, val message: String? = null, val number: String? = null)

data class SlotDto(
    val id: Int = 0,
    val title: String = "",
    val start: String = "",
    val duration_min: Int = 60,
    val capacity: Int = 0,
    val booked: Int = 0,
    val price: Int = 0,
    val group_only: Boolean = false,
)

/** Онлайн-дополнения к карточке: отзывы, промокоды, расписание экскурсий, истории. Кэшируются для офлайна. */
data class PlaceExtra(
    val reviews: List<ReviewDto>? = null,
    val promos: List<PromoDto>? = null,
    val slots: List<SlotDto>? = null,
    val stories: List<StoryDto>? = null,
    val opportunities: List<OpportunityDto>? = null,
    val events: List<EventDto>? = null,
    val is_owner: Boolean = false,
    val my_review: ReviewDto? = null,
    val rating: Double = 0.0,
    val reviews_count: Int = 0,
)
