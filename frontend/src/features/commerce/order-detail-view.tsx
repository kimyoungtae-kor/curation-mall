import { resolveApiAssetUrl } from "@/lib/api/client";
import {
  formatDateTime,
  formatWon,
  orderStatusLabel,
  paymentStatusLabel,
} from "./format";
import type { OrderDetail } from "./types";

export function OrderDetailView({ order }: { order: OrderDetail }) {
  return (
    <div className="commerce-order-detail">
      <section className="commerce-panel commerce-order-heading">
        <div>
          <p className="commerce-eyebrow">ORDER</p>
          <h1>주문 {order.orderNumber}</h1>
          <p>{formatDateTime(order.orderedAt)} 주문</p>
        </div>
        <div className="commerce-statuses" aria-label="주문 상태">
          <span data-tone={order.orderStatus === "CANCELLED" ? "danger" : "brand"}>
            {orderStatusLabel(order.orderStatus)}
          </span>
          <span>{paymentStatusLabel(order.paymentStatus)}</span>
        </div>
      </section>

      <section className="commerce-panel" aria-labelledby="order-products-title">
        <h2 id="order-products-title">주문 상품</h2>
        <div className="commerce-order-items">
          {order.items.map((item, index) => {
            const imageUrl = resolveApiAssetUrl(item.imageUrl);
            return (
              <article className="commerce-order-item" key={`${item.sku}-${index}`}>
                <div className="commerce-order-item__image" aria-hidden={!imageUrl}>
                  {imageUrl ? (
                    // The deployment media host is not fixed yet.
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={imageUrl} alt="" />
                  ) : (
                    <span>{item.productName.slice(0, 1)}</span>
                  )}
                </div>
                <div>
                  <p className="commerce-muted">{item.brandName}</p>
                  <h3>{item.productName}</h3>
                  <p>
                    {item.optionLabel} · {item.quantity}개
                  </p>
                </div>
                <strong>{formatWon(item.lineAmount)}</strong>
              </article>
            );
          })}
        </div>
      </section>

      <div className="commerce-detail-columns">
        <section className="commerce-panel" aria-labelledby="shipping-title">
          <h2 id="shipping-title">배송 정보</h2>
          <dl className="commerce-definition-list">
            <div>
              <dt>받는 분</dt>
              <dd>{order.shipping.recipientName}</dd>
            </div>
            <div>
              <dt>연락처</dt>
              <dd>{order.shipping.recipientPhone}</dd>
            </div>
            <div>
              <dt>주소</dt>
              <dd>
                ({order.shipping.postalCode}) {order.shipping.address1}{" "}
                {order.shipping.address2}
              </dd>
            </div>
            {order.shipping.deliveryMessage ? (
              <div>
                <dt>배송 요청</dt>
                <dd>{order.shipping.deliveryMessage}</dd>
              </div>
            ) : null}
          </dl>
        </section>

        <section className="commerce-panel" aria-labelledby="payment-summary-title">
          <h2 id="payment-summary-title">결제 금액</h2>
          <dl className="commerce-price-list">
            <div>
              <dt>상품 금액</dt>
              <dd>{formatWon(order.itemsAmount)}</dd>
            </div>
            <div>
              <dt>할인</dt>
              <dd>-{formatWon(order.discountAmount)}</dd>
            </div>
            <div>
              <dt>배송비</dt>
              <dd>{formatWon(order.shippingAmount)}</dd>
            </div>
            <div className="commerce-price-list__total">
              <dt>총 결제 금액</dt>
              <dd>{formatWon(order.totalAmount)}</dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  );
}
