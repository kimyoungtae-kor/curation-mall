"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  formatDateTime,
  formatWon,
  orderStatusLabel,
  paymentStatusLabel,
} from "@/features/commerce/format";
import type { OrderStatus } from "@/features/commerce/types";
import { getAdminOrder, getAdminOrders, transitionAdminOrder } from "./api";
import { AdminGuard } from "./admin-shell";
import { AdminEmpty, AdminLoading } from "./dashboard";
import { adminErrorMessage } from "./error";
import { allowedNextOrderStatus } from "./order-transitions";
import type { AdminOrderDetail, AdminOrderSummary } from "./types";

const ORDER_STATUSES: { value: OrderStatus | ""; label: string }[] = [
  { value: "", label: "전체 상태" },
  { value: "PENDING_PAYMENT", label: "결제 대기" },
  { value: "PAID", label: "결제 완료" },
  { value: "PREPARING", label: "상품 준비" },
  { value: "SHIPPED", label: "배송 중" },
  { value: "DELIVERED", label: "배송 완료" },
  { value: "CANCEL_REQUESTED", label: "취소 요청" },
  { value: "CANCELLED", label: "취소 완료" },
];

export function AdminOrderListPage() {
  return (
    <AdminGuard title="주문 관리" description="회원·비회원 주문을 조회하고 허용된 다음 상태로 변경합니다.">
      <OrderList />
    </AdminGuard>
  );
}

function OrderList() {
  const [draftQuery, setDraftQuery] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [page, setPage] = useState(0);
  const [orders, setOrders] = useState<AdminOrderSummary[]>([]);
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminOrderDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getAdminOrders({ q: query, status, page, size: 20 }, controller.signal)
      .then((response) => {
        setOrders(response.data);
        setPageInfo(response.page);
        setError(null);
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(adminErrorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, query, status]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setQuery(draftQuery.trim());
  }

  async function openDetail(orderNumber: string) {
    if (detail?.order.orderNumber === orderNumber) {
      setDetail(null);
      return;
    }
    setDetailLoading(true);
    setDetailError(null);
    try {
      setDetail(await getAdminOrder(orderNumber));
    } catch (caught) {
      setDetailError(adminErrorMessage(caught));
    } finally {
      setDetailLoading(false);
    }
  }

  function replaceSummary(updated: AdminOrderDetail) {
    setOrders((current) => current.map((summary) => summary.orderNumber === updated.order.orderNumber
      ? {
          ...summary,
          orderStatus: updated.order.orderStatus,
          paymentStatus: updated.order.paymentStatus,
        }
      : summary));
  }

  return (
    <div className="admin-stack">
      <form className="admin-toolbar" onSubmit={search}>
        <label className="admin-toolbar__search">
          <span className="admin-sr-only">주문 검색</span>
          <input type="search" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} placeholder="주문번호 또는 구매자명 검색" />
        </label>
        <label>
          <span className="admin-sr-only">주문 상태</span>
          <select value={status} onChange={(event) => { setPage(0); setStatus(event.target.value as OrderStatus | ""); }}>
            {ORDER_STATUSES.map((option) => <option key={option.value || "all"} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button className="admin-button" type="submit">검색</button>
        <span className="admin-toolbar__count">총 {pageInfo.totalElements.toLocaleString("ko-KR")}건</span>
      </form>

      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
      {loading ? <AdminLoading label="주문 목록을 불러오는 중입니다." /> : orders.length ? (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>주문번호</th><th>구매자</th><th>금액</th><th>주문 상태</th><th>결제 상태</th><th>주문 일시</th><th><span className="admin-sr-only">상세</span></th></tr></thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.orderNumber} data-selected={detail?.order.orderNumber === order.orderNumber}>
                  <td><strong>{order.orderNumber}</strong><span>{order.orderType === "MEMBER" ? "회원" : "비회원"} 주문</span></td>
                  <td>{order.buyerName}</td>
                  <td><strong>{formatWon(order.totalAmount)}</strong></td>
                  <td><span className="admin-status" data-status={order.orderStatus}>{orderStatusLabel(order.orderStatus)}</span></td>
                  <td>{paymentStatusLabel(order.paymentStatus)}</td>
                  <td>{formatDateTime(order.orderedAt)}</td>
                  <td><button className="admin-table-action" type="button" onClick={() => void openDetail(order.orderNumber)}>{detail?.order.orderNumber === order.orderNumber ? "닫기" : "상세"}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <AdminEmpty label="조건에 맞는 주문이 없습니다." />}

      {pageInfo.totalPages > 1 ? (
        <nav className="admin-pagination" aria-label="주문 페이지">
          <button type="button" disabled={pageInfo.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>이전</button>
          <span>{page + 1} / {pageInfo.totalPages}</span>
          <button type="button" disabled={pageInfo.last} onClick={() => setPage((current) => current + 1)}>다음</button>
        </nav>
      ) : null}

      {detailLoading ? <AdminLoading label="주문 상세를 불러오는 중입니다." /> : null}
      {detailError ? <p className="admin-alert admin-alert--error" role="alert">{detailError}</p> : null}
      {detail ? (
        <OrderDetailPanel
          detail={detail}
          onUpdated={(updated) => {
            setDetail(updated);
            replaceSummary(updated);
          }}
        />
      ) : null}
    </div>
  );
}

function OrderDetailPanel({ detail, onUpdated }: { detail: AdminOrderDetail; onUpdated: (detail: AdminOrderDetail) => void }) {
  const order = detail.order;
  const nextStatus = allowedNextOrderStatus(order.orderStatus);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function transition() {
    if (!nextStatus || !reason.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await transitionAdminOrder(order.orderNumber, nextStatus, reason.trim(), detail.version);
      onUpdated(updated);
      setReason("");
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="admin-panel admin-order-detail" aria-labelledby="order-detail-heading">
      <div className="admin-order-detail__heading">
        <div>
          <p className="admin-kicker">{order.orderType === "MEMBER" ? "MEMBER ORDER" : "GUEST ORDER"}</p>
          <h2 id="order-detail-heading">주문 {order.orderNumber}</h2>
          <p>{formatDateTime(order.orderedAt)}</p>
        </div>
        <div className="admin-order-detail__statuses">
          <span className="admin-status" data-status={order.orderStatus}>{orderStatusLabel(order.orderStatus)}</span>
          <span>{paymentStatusLabel(order.paymentStatus)}</span>
        </div>
      </div>

      {nextStatus ? (
        <div className="admin-transition-box">
          <div><strong>다음 상태: {orderStatusLabel(nextStatus)}</strong><span>허용된 순서에 따라 한 단계씩 변경합니다.</span></div>
          <label><span className="admin-sr-only">변경 사유</span><input value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="변경 사유를 입력하세요" /></label>
          <button className="admin-button admin-button--primary" type="button" disabled={submitting || !reason.trim()} onClick={() => void transition()}>{submitting ? "변경 중…" : "상태 변경"}</button>
        </div>
      ) : <p className="admin-alert">현재 상태에서는 관리자가 진행할 다음 단계가 없습니다.</p>}
      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}

      <div className="admin-order-columns">
        <div>
          <h3>구매자</h3>
          <dl className="admin-definition-list">
            <div><dt>이름</dt><dd>{order.buyer.name}</dd></div>
            <div><dt>이메일</dt><dd>{order.buyer.email}</dd></div>
            <div><dt>연락처</dt><dd>{order.buyer.phone}</dd></div>
          </dl>
        </div>
        <div>
          <h3>배송지</h3>
          <dl className="admin-definition-list">
            <div><dt>받는 분</dt><dd>{order.shipping.recipientName} · {order.shipping.recipientPhone}</dd></div>
            <div><dt>주소</dt><dd>({order.shipping.postalCode}) {order.shipping.address1} {order.shipping.address2}</dd></div>
            {order.shipping.deliveryMessage ? <div><dt>요청</dt><dd>{order.shipping.deliveryMessage}</dd></div> : null}
          </dl>
        </div>
      </div>

      <div className="admin-order-items">
        <h3>주문 상품</h3>
        {order.items.map((item, index) => (
          <article key={`${item.sku}-${index}`}>
            <div><strong>{item.productName}</strong><span>{item.brandName} · {item.optionLabel} · {item.quantity}개</span></div>
            <strong>{formatWon(item.lineAmount)}</strong>
          </article>
        ))}
      </div>

      <div className="admin-order-columns">
        <div>
          <h3>처리 이력</h3>
          {order.statusHistory.length ? (
            <ol className="admin-history">
              {order.statusHistory.map((history, index) => (
                <li key={`${history.toStatus}-${history.createdAt}-${index}`}>
                  <strong>{history.fromStatus ? `${orderStatusLabel(history.fromStatus)} → ` : ""}{orderStatusLabel(history.toStatus)}</strong>
                  <span>{history.reason || "시스템 처리"} · {formatDateTime(history.createdAt)}</span>
                </li>
              ))}
            </ol>
          ) : <p className="admin-empty admin-empty--compact">처리 이력이 없습니다.</p>}
        </div>
        <div>
          <h3>결제 금액</h3>
          <dl className="admin-price-list">
            <div><dt>상품</dt><dd>{formatWon(order.itemsAmount)}</dd></div>
            <div><dt>할인</dt><dd>-{formatWon(order.discountAmount)}</dd></div>
            <div><dt>배송</dt><dd>{formatWon(order.shippingAmount)}</dd></div>
            <div><dt>총 결제</dt><dd>{formatWon(order.totalAmount)}</dd></div>
          </dl>
        </div>
      </div>
    </section>
  );
}
