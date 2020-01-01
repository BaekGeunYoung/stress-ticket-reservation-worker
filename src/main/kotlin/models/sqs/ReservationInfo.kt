package models.sqs

import java.time.LocalDateTime

data class ReservationInfo(
    val event_name: String,
    val event_datetime: LocalDateTime,
    val event_common: EventCommon,
    val event_dic: EventDetail
)