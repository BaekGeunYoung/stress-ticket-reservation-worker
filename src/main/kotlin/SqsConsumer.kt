import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import models.db.Event
import models.db.FailedReservation
import models.db.Reservation
import models.sqs.ReservationInfo
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.lang.Thread.currentThread
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class SqsConsumer (private val sqs: SqsAsyncClient): CoroutineScope {
    /**
     * coroutine context 정의 :
     * DB IO가 주된 작업이 될 것이므로 IO dispatcher 사용.
     * coroutine의 exception 전파 방향이 아래로만 이루어지도록 supervisorJob 사용.
     */
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    /**
     * json string으로 넘어오는 queue message를 파싱하기 위한 object mapper.
     */
    private val jsonMapper = jacksonObjectMapper()

    /**
     * dynamoDB client
     */
    private val ddbClient: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withRegion(Regions.AP_NORTHEAST_2)
        .build()

    /**
     * dynamoDB mapper
     */
    private val ddbMapper = DynamoDBMapper(ddbClient)

    /**
     * mutex class instance for mutual exclusion
     */
    private val mutex = Mutex()

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

    fun start() = runBlocking {
        val messageChannel = Channel<Message>()
        repeat(Constants.numOfProcessors) {
            launchProcessor(messageChannel)
        }
        launchMsgReceiver(messageChannel)
    }

    //MsgReceiver : SQS로 주기적으로 polling하여 큐 메세지를 받아오고, channel을 통해 processor로 큐 메세지를 넘긴다.
    private fun CoroutineScope.launchMsgReceiver(channel: SendChannel<Message>) = launch {
        repeatUntilCancelled {
            val receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(Constants.sqsEndpoint)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(10)
                .build()

            val messages = sqs.receiveMessage(receiveRequest).await().messages()
            println("${currentThread().name} Retrieved ${messages.size} messages")

            messages.forEach {
                channel.send(it)
            }
        }
    }

    //큐메세지를 polling하는 작업을 무한히 반복할 수 있도록 하는 coroutine.
    private suspend fun CoroutineScope.repeatUntilCancelled(suspendFunc: suspend() -> Unit) {
        while(isActive) {
            try {
                suspendFunc()
                yield()
            } catch (ex: CancellationException) {
                println("coroutine on ${currentThread().name} cancelled")
            } catch (ex: Exception) {
                println("${currentThread().name} failed with {$ex}. Retrying...")
                ex.printStackTrace()
            }
        }

        println("coroutine on ${currentThread().name} exiting")
    }

    //Processor : 큐 메세지를 받아 실제 작업을 처리.
    private fun CoroutineScope.launchProcessor(channel: ReceiveChannel<Message>) = launch {
        repeatUntilCancelled {
            for (msg in channel) {
                try {
                    //공연의 남은 좌석 개수를 관리하기 위해 mutex 활용
                    mutex.withLock {
                        processMsg(msg)
                    }
                    deleteMessage(msg)
                } catch (ex: Exception) {
                    println("${currentThread().name} exception trying to process message ${msg.body()}")
                    ex.printStackTrace()
                    changeVisibility(msg)
                }
            }
        }
    }

    //큐 메세지를 처리하는 예시 코드
    //dynamoDB에 데이터 저장
    private fun processMsg(message: Message) {
        try{
            //mapper를 이용해 queue message를 ReservationInfo 객체로 parsing 한다.
            val reservationInfo: ReservationInfo = jsonMapper.readValue(message.body())
            val reservationDatetime = LocalDateTime.now()
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

        for(eventItem in eventItems) {
            when(eventItem.event_name) {
                Constants.LOGIN -> lastLoginDatetime = eventItem.lastLoginDatetime
                Constants.LOGOUT -> lastLogoutDatetime = eventItem.lastLogoutDatetime
                Constants.PAGE_VIEW -> lastPageViewDatetime = eventItem.lastPageViewDatetime
            }
        }

        return (
            lastLoginDatetime != null && lastPageViewDatetime != null
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

    private suspend fun deleteMessage(message: Message) {
        sqs.deleteMessage { req ->
            req.queueUrl(Constants.sqsEndpoint)
            req.receiptHandle(message.receiptHandle())
        }.await()
        println("${currentThread().name} Message deleted: ${message.body()}")
    }

    private suspend fun changeVisibility(message: Message) {
        sqs.changeMessageVisibility { req ->
            req.queueUrl(Constants.sqsEndpoint)
            req.receiptHandle(message.receiptHandle())
            req.visibilityTimeout(10)
        }.await()
        println("${currentThread().name} Changed visibility of message: ${message.body()}")
    }
}