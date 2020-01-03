package test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import models.sqs.ReservationInfo
import java.time.LocalDateTime

fun main() {
    val json = "{\"event_name\":\"RESERVE_TICKET\",\"event_datetime\":\"2020-01-03T16:51:46.066358700\",\"event_common\":{\"event_id\":\"6\"},\"event_dic\":{\"user_id\":\"geunyoung\",\"concert_id\":0,\"ticket_num\":1}}"
    val jsonMapper = jacksonObjectMapper()

    val reservationInfo: ReservationInfo = jsonMapper.readValue(json)

    println(LocalDateTime.parse(reservationInfo.event_datetime))
}