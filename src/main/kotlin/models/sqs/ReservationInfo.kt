package models.sqs

data class ReservationInfo(
    val event_id: String,
    val user_id: String,
    val concert_id: String,
    val ticket_num: Int
)