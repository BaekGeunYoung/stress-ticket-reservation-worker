package test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import models.sqs.ReservationInfo

fun main() {
    val jsonMapper = jacksonObjectMapper()
    val reservationInfo = ReservationInfo(event_id = 1, user_id = "geunyoung", ticket_num = 1)
    val messageBody = jsonMapper.writeValueAsString(reservationInfo)
    println(messageBody)
}