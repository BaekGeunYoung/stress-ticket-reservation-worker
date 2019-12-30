package models.sqs

data class ReservationInfo(
    val event_id: Int,
    val user_id: String,
    val ticket_num: Int
)