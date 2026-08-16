package kr.co.petcuration.payment.application;

import kr.co.petcuration.common.api.ApiException;
import org.springframework.http.HttpStatus;

final class PaymentReservationExpiredException extends ApiException {

    PaymentReservationExpiredException() {
        super(HttpStatus.CONFLICT, "ORDER_RESERVATION_EXPIRED", "결제 가능 시간이 지났습니다.",
                "장바구니에서 재고와 가격을 다시 확인한 뒤 주문해 주세요.");
    }
}
