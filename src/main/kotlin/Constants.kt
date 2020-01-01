class Constants {
    companion object {
        const val numOfProcessors = 100
        const val sqsEndpoint = "https://sqs.ap-northeast-2.amazonaws.com/182756308452/ticket_reservation_data_queue"

        const val MAX_CONCERT_SEATS = 1000

        const val LOGIN = "LOGIN"
        const val LOGOUT = "LOGOUT"
        const val PAGE_VIEW = "PAGE_VIEW"
        const val TICKET_RESERVATION = "TICKET_RESERVATION"
    }
}