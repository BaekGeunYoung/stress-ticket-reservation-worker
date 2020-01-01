package models.sqs

data class EventDetail(
    var user_id: String,
    var concert_id: Int,
    var ticket_num: Int
)