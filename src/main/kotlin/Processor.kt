import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import models.db.Event
import models.db.FailedReservation
import models.db.Reservation
import models.sqs.ReservationInfo
import software.amazon.awssdk.services.sqs.model.Message
import java.time.LocalDateTime

class Processor (
    private val jsonMapper: ObjectMapper,
    private val ddbMapper: DynamoDBMapper
) {
    /**
     * 좌석 갯수 관리를 위한 변수 선언
     */
    private var seatsCnt0 = getSeatsCnt(0, ddbMapper)
    private var seatsCnt1 = getSeatsCnt(1, ddbMapper)
    private var seatsCnt2 = getSeatsCnt(2, ddbMapper)

    private fun getSeatsCnt(concertId: Int, ddbMapper: DynamoDBMapper): Int {
        val eav = HashMap<String, AttributeValue>()
        eav[":concertId"] = AttributeValue().withN(concertId.toString())

        val scanExpression = DynamoDBScanExpression()
            .withFilterExpression("concert_id = :concertId")
            .withExpressionAttributeValues(eav)

        return ddbMapper.scan(Reservation::class.java, scanExpression).size
    }

    fun processMsg(message: Message) {
        try {
            //mapper를 이용해 queue message를 ReservationInfo 객체로 parsing 한다.
            val reservationInfo: ReservationInfo = jsonMapper.readValue(message.body())
            val reservationDatetime = LocalDateTime.parse(reservationInfo.event_datetime)
            val reservation = Reservation(
                user_id = reservationInfo.event_dic.user_id,
                reservation_datetime = reservationDatetime,
                concert_id = reservationInfo.event_dic.concert_id
            )

            val ticketNum = reservationInfo.event_dic.ticket_num

            if(isValidScenario(reservationInfo, reservationDatetime)) {
                saveReservation(reservation, ticketNum)
                println("saved $ticketNum successful reservation(s)")
            }
            else {
                saveReservation(reservation.toFailedReservation(), ticketNum)
                println("saved $ticketNum failed reservation(s)")
            }
        } catch (e : JsonParseException) {
            println("${message.body()} cannot be parsed into ReservationInfo class")
        }
    }

    private fun isValidScenario(
        reservationInfo: ReservationInfo,
        reservationDatetime: LocalDateTime
    ) : Boolean {
        //partition key를 통한 event table 조회
        val eventItems = getEventPartition(reservationInfo.event_common.event_id)

        val targetConcertSeats = when(reservationInfo.event_dic.concert_id) {
            0 -> seatsCnt0
            1 -> seatsCnt1
            else -> seatsCnt2
        }

        var lastLoginDatetime: LocalDateTime? = null
        var lastLogoutDatetime: LocalDateTime? = null
        var lastPageViewDatetime: LocalDateTime? = null
        var pageViewConcertId: Int? = null

        for(eventItem in eventItems) {
            when(eventItem.event_name) {
                Constants.LOGIN -> lastLoginDatetime = eventItem.lastLoginDatetime
                Constants.LOGOUT -> lastLogoutDatetime = eventItem.lastLogoutDatetime
                Constants.PAGE_VIEW -> {
                    lastPageViewDatetime = eventItem.lastPageViewDatetime
                    pageViewConcertId = eventItem.pageViewConcertId
                }
            }
        }

        return (
                lastLoginDatetime != null && lastPageViewDatetime != null && pageViewConcertId != null
                        && pageViewConcertId == reservationInfo.event_dic.concert_id
                        && (lastLogoutDatetime == null || lastLogoutDatetime < lastLoginDatetime)
                        && lastPageViewDatetime < reservationDatetime
                        && lastLoginDatetime < lastPageViewDatetime
                        && reservationInfo.event_dic.ticket_num + targetConcertSeats <= Constants.MAX_CONCERT_SEATS
                )
    }

    private fun saveReservation(reservation: Reservation, ticketNum : Int) {
        for(i in (0 until ticketNum)) {
            ddbMapper.save(reservation)
        }

        when(reservation.concert_id) {
            0 -> seatsCnt0 += ticketNum
            1 -> seatsCnt1 += ticketNum
            2 -> seatsCnt2 += ticketNum
        }
    }

    private fun saveReservation(reservation: FailedReservation, ticketNum : Int) {
        for(i in (0 until ticketNum)) {
            ddbMapper.save(reservation)
        }
    }

    private fun getEventPartition(eventId: String): List<Event> {
        val eav = HashMap<String, AttributeValue>()
        eav[":eventId"] = AttributeValue(eventId)

        val scanExpression = DynamoDBScanExpression()
            .withFilterExpression("event_id = :eventId")
            .withExpressionAttributeValues(eav)

        return ddbMapper.scan(Event::class.java, scanExpression)
    }
}